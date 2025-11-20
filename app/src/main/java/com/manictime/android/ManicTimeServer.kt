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
import java.io.File

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
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
            }
            ACTION_START_SCREENSHOT -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (data != null) {
                    startScreenshot(resultCode, data)
                }
            }
            "TEST_SCREENSHOT" -> {
                // 立即执行一次截图测试
                serviceScope.launch {
                    try {
                        takeScreenshot()
                        Log.d(TAG, "测试截图完成")
                    } catch (e: Exception) {
                        Log.e(TAG, "测试截图失败", e)
                    }
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        
        // 清理截图资源
        cleanupScreenshot()
        
        // 如果启用了自动启动，则重启服务
        if (prefs.autoStartEnabled) {
            Log.d(TAG, "服务被销毁，准备重启")
            val restartIntent = Intent(applicationContext, ManicTimeService::class.java).apply {
                action = ACTION_START
            }
            
            val pendingIntent = PendingIntent.getService(
                applicationContext,
                0,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000,
                pendingIntent
            )
        }
        
        stopMonitoring()
        serviceScope.cancel()
        isRunning = false
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed")
        
        // 任务被移除时，如果启用自动启动则重启服务
        if (prefs.autoStartEnabled) {
            Log.d(TAG, "任务被移除，重启服务")
            val restartIntent = Intent(applicationContext, ManicTimeService::class.java).apply {
                action = ACTION_START
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        }
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
        
        // 启动截图文件扫描
        screenshotJob = serviceScope.launch {
            while (isActive) {
                delay(10_000L) // 每10秒扫描一次截图目录
                try {
                    scanScreenshotFiles()
                } catch (e: Exception) {
                    Log.e(TAG, "扫描截图文件失败", e)
                }
            }
        }
    }
    
    /**
     * 扫描辅助功能服务生成的截图文件
     */
    private suspend fun scanScreenshotFiles() = withContext(Dispatchers.IO) {
        try {
            val screenshotsDir = File(externalCacheDir ?: cacheDir, "screenshots")
            if (!screenshotsDir.exists()) {
                return@withContext
            }
            
            val files = screenshotsDir.listFiles { file ->
                file.name.startsWith("screenshot_") && file.name.endsWith(".jpg")
            } ?: return@withContext
            
            for (file in files) {
                try {
                    // 读取截图文件
                    val bytes = file.readBytes()
                    val timestamp = file.name
                        .removePrefix("screenshot_")
                        .removeSuffix(".jpg")
                        .toLongOrNull() ?: System.currentTimeMillis()
                    
                    // 添加到上传队列
                    val screenshot = ScreenshotData(
                        timestamp = timestamp,
                        imageData = bytes
                    )
                    screenshotQueue.add(screenshot)
                    
                    // 删除已处理的文件
                    file.delete()
                    
                    Log.d(TAG, "扫描到截图: ${file.name}, 大小: ${bytes.size / 1024}KB")
                } catch (e: Exception) {
                    Log.e(TAG, "处理截图文件失败: ${file.name}", e)
                }
            }
            
            if (files.isNotEmpty()) {
                updateNotification("待上传 ${screenshotQueue.size} 张截图")
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描截图目录失败", e)
        }
    }
    
    private suspend fun checkCurrentActivity() = withContext(Dispatchers.IO) {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) 
                as android.app.usage.UsageStatsManager
            
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 60_000 // 最近1分钟
            
            val stats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_BEST,
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
        
        activityMonitorJob = null
        screenshotJob = null
        uploadJob = null
    }
    
    private fun startScreenshot(resultCode: Int, data: Intent) {
        Log.d(TAG, "启动截图功能")
        
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        
        if (mediaProjection == null) {
            Log.e(TAG, "获取MediaProjection失败")
            return
        }
        
        screenshotJob = serviceScope.launch {
            delay(5000)
            
            while (isActive && prefs.screenshotEnabled) {
                try {
                    takeScreenshot()
                } catch (e: Exception) {
                    Log.e(TAG, "截图失败", e)
                }
                delay(prefs.screenshotInterval)
            }
        }
        
        updateNotification("截图功能已启用")
    }
    
    private suspend fun takeScreenshot() {
        if (mediaProjection == null) {
            Log.w(TAG, "MediaProjection未初始化")
            return
        }
        
        try {
            val screenshotHelper = MediaProjectionScreenshot(this, mediaProjection!!)
            val file = screenshotHelper.captureScreen()
            
            if (file != null && file.exists()) {
                Log.d(TAG, "截图成功: ${file.name}, 大小: ${file.length() / 1024}KB")
                
                val screenshotManager = ScreenshotManager(this)
                val result = screenshotManager.saveScreenshot(file)
                
                if (result != null) {
                    val (originalFile, thumbnailFile) = result
                    screenshotManager.markForUpload(originalFile, thumbnailFile)
                    Log.d(TAG, "截图已保存: ${originalFile.name}")
                }
                
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "截图过程出错", e)
        }
    }
    
    private fun cleanupScreenshot() {
        try {
            screenshotJob?.cancel()
            mediaProjection?.stop()
            virtualDisplay?.release()
            imageReader?.close()
            
            mediaProjection = null
            virtualDisplay = null
            imageReader = null
            
            Log.d(TAG, "截图资源已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理截图资源失败", e)
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