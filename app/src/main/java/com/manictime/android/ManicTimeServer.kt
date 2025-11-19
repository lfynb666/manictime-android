package com.manictime.android

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * ManicTime 前台服务
 * 负责:
 * 1. 监控应用使用情况
 * 2. 定时截图
 * 3. 上传数据到服务器
 */
class ManicTimeService : Service() {
    companion object {
        const val TAG = "ManicTimeService"
        const val CHANNEL_ID = "ManicTimeChannel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "com.manictime.android.START"
        const val ACTION_STOP = "com.manictime.android.STOP"
        const val ACTION_START_SCREENSHOT = "com.manictime.android.START_SCREENSHOT"
        
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        
        // 监控间隔
        const val ACTIVITY_CHECK_INTERVAL = 30_000L // 30秒检查一次应用
        const val SCREENSHOT_INTERVAL = 300_000L // 5分钟截图一次
        const val UPLOAD_INTERVAL = 60_000L // 1分钟上传一次
        
        var isRunning = false
    }
    
    private lateinit var prefs: ManicTimePreferences
    private lateinit var apiClient: ManicTimeApiClient
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 应用监控
    private var activityMonitorJob: Job? = null
    private var lastActiveApp: String? = null
    private var lastActivityTime = 0L
    
    // 截图相关
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenshotJob: Job? = null
    private var screenshotResultCode: Int = 0
    private var screenshotResultData: Intent? = null
    
    // 数据缓存
    private val activityQueue = mutableListOf<ActivityRecord>()
    private val screenshotQueue = mutableListOf<ScreenshotData>()
    private var uploadJob: Job? = null
    
    // Timeline信息
    private var timelineKey: String? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        prefs = ManicTimePreferences(this)
        apiClient = ManicTimeApiClient(prefs)
        
        createNotificationChannel()
        isRunning = true
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification("正在运行"))
                startMonitoring()
            }
            ACTION_START_SCREENSHOT -> {
                screenshotResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                screenshotResultData = intent.getParcelableExtra(EXTRA_RESULT_DATA)
                startScreenshotCapture()
            }
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        stopMonitoring()
        serviceScope.cancel()
        isRunning = false
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ManicTime监控服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "记录应用使用和截图"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ManicTime正在运行")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun startMonitoring() {
        Log.d(TAG, "开始监控")
        
        // 初始化timeline
        serviceScope.launch {
            try {
                timelineKey = apiClient.getOrCreateTimeline()
                Log.d(TAG, "Timeline Key: $timelineKey")
            } catch (e: Exception) {
                Log.e(TAG, "获取timeline失败", e)
            }
        }
        
        // 启动应用活动监控
        activityMonitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkCurrentActivity()
                } catch (e: Exception) {
                    Log.e(TAG, "监控应用失败", e)
                }
                delay(ACTIVITY_CHECK_INTERVAL)
            }
        }
        
        // 启动数据上传
        uploadJob = serviceScope.launch {
            while (isActive) {
                delay(UPLOAD_INTERVAL)
                try {
                    uploadPendingData()
                } catch (e: Exception) {
                    Log.e(TAG, "上传数据失败", e)
                }
            }
        }
    }
    
    private fun startScreenshotCapture() {
        if (screenshotResultCode == -1 || screenshotResultData == null) {
            Log.e(TAG, "截图权限数据无效")
            return
        }
        
        Log.d(TAG, "开始截图服务")
        
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) 
            as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(
            screenshotResultCode,
            screenshotResultData!!
        )
        
        setupImageReader()
        
        screenshotJob = serviceScope.launch {
            delay(5000) // 等待5秒后开始第一次截图
            while (isActive) {
                try {
                    captureScreenshot()
                } catch (e: Exception) {
                    Log.e(TAG, "截图失败", e)
                }
                delay(SCREENSHOT_INTERVAL)
            }
        }
    }
    
    private fun setupImageReader() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        
        // 缩小分辨率以减少文件大小
        val scaledWidth = width / 2
        val scaledHeight = height / 2
        
        imageReader = ImageReader.newInstance(
            scaledWidth,
            scaledHeight,
            PixelFormat.RGBA_8888,
            2
        )
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ManicTimeCapture",
            scaledWidth,
            scaledHeight,
            density / 2,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }
    
    private suspend fun captureScreenshot() = withContext(Dispatchers.IO) {
        try {
            val image = imageReader?.acquireLatestImage() ?: return@withContext
            
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            
            // 裁剪多余部分
            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                0, 0,
                image.width,
                image.height
            )
            
            // 压缩为JPEG
            val stream = ByteArrayOutputStream()
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            val bytes = stream.toByteArray()
            
            bitmap.recycle()
            croppedBitmap.recycle()
            
            // 添加到队列
            val screenshot = ScreenshotData(
                timestamp = System.currentTimeMillis(),
                imageData = bytes
            )
            screenshotQueue.add(screenshot)
            
            Log.d(TAG, "截图成功: ${bytes.size / 1024}KB, 队列大小: ${screenshotQueue.size}")
            updateNotification("已截图 ${screenshotQueue.size} 张")
            
        } catch (e: Exception) {
            Log.e(TAG, "截图处理失败", e)
        }
    }
    
    private suspend fun checkCurrentActivity() = withContext(Dispatchers.IO) {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) 
                as UsageStatsManager
            
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 60_000 // 最近1分钟
            
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                endTime
            )
            
            // 获取最近使用的应用
            val recentApp = stats?.maxByOrNull { it.lastTimeUsed }
            
            if (recentApp != null) {
                val packageName = recentApp.packageName
                val appName = try {
                    val pm = packageManager
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    packageName
                }
                
                val currentTime = System.currentTimeMillis()
                
                // 如果应用改变或时间间隔超过阈值,记录活动
                if (packageName != lastActiveApp || 
                    (currentTime - lastActivityTime) > ACTIVITY_CHECK_INTERVAL) {
                    
                    if (lastActiveApp != null) {
                        // 保存上一个活动
                        val duration = (currentTime - lastActivityTime) / 1000 // 秒
                        if (duration > 0) {
                            activityQueue.add(
                                ActivityRecord(
                                    appName = lastActiveApp!!,
                                    packageName = lastActiveApp!!,
                                    startTime = lastActivityTime,
                                    duration = duration
                                )
                            )
                            Log.d(TAG, "记录活动: $lastActiveApp, 时长: ${duration}秒")
                        }
                    }
                    
                    lastActiveApp = appName
                    lastActivityTime = currentTime
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查当前活动失败", e)
        }
    }
    
    private suspend fun uploadPendingData() = withContext(Dispatchers.IO) {
        if (timelineKey == null) {
            Log.w(TAG, "Timeline未初始化,跳过上传")
            return@withContext
        }
        
        // 上传活动记录
        if (activityQueue.isNotEmpty()) {
            try {
                val activities = activityQueue.toList()
                activityQueue.clear()
                
                for (activity in activities) {
                    apiClient.uploadActivity(timelineKey!!, activity)
                }
                
                Log.d(TAG, "上传了 ${activities.size} 条活动记录")
                updateNotification("已同步 ${activities.size} 条活动")
            } catch (e: Exception) {
                Log.e(TAG, "上传活动失败", e)
                // 失败则放回队列
                activityQueue.addAll(0, activityQueue)
            }
        }
        
        // 上传截图
        if (screenshotQueue.isNotEmpty()) {
            try {
                val screenshots = screenshotQueue.take(3).toList() // 一次最多上传3张
                
                for (screenshot in screenshots) {
                    apiClient.uploadScreenshot(screenshot)
                    screenshotQueue.remove(screenshot)
                }
                
                Log.d(TAG, "上传了 ${screenshots.size} 张截图")
                updateNotification("已上传 ${screenshots.size} 张截图")
            } catch (e: Exception) {
                Log.e(TAG, "上传截图失败", e)
            }
        }
    }
    
    private fun stopMonitoring() {
        Log.d(TAG, "停止监控")
        
        activityMonitorJob?.cancel()
        screenshotJob?.cancel()
        uploadJob?.cancel()
        
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        
        activityMonitorJob = null
        screenshotJob = null
        uploadJob = null
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
}

// 数据类
data class ActivityRecord(
    val appName: String,
    val packageName: String,
    val startTime: Long,
    val duration: Long // 秒
)

data class ScreenshotData(
    val timestamp: Long,
    val imageData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ScreenshotData
        return timestamp == other.timestamp
    }
    
    override fun hashCode(): Int = timestamp.hashCode()
}