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
        const val ACTION_MANUAL_UPLOAD = "com.manictime.android.MANUAL_UPLOAD"
        
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        
        // 监控间隔
        const val ACTIVITY_CHECK_INTERVAL = 30_000L // 30秒检查一次应用
        const val SCREENSHOT_INTERVAL = 300_000L // 5分钟截图一次
        const val UPLOAD_INTERVAL = 60_000L // 1分钟上传一次
        
        var isRunning = false
        private var instance: ManicTimeService? = null
        
        fun getInstance(): ManicTimeService? = instance
    }
    
    private lateinit var prefs: ManicTimePreferences
    private lateinit var apiClient: ManicTimeApiClient
    private lateinit var screenshotUploader: ScreenshotUploader
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 应用监控
    private var activityMonitorJob: Job? = null
    private var lastActiveApp: String? = null  // 存储packageName
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
    private var lastChangeId: String? = null
    private var environmentId: String? = null
    
    // 设备状态监听
    private var deviceStateReceiver: DeviceStateReceiver? = null
    private var lastDeviceState: DeviceState? = null
    private var deviceStateStartTime = 0L
    
    // Documents数据缓存
    private val documentsQueue = mutableListOf<DocumentRecord>()
    private var lastDocument: DocumentRecord? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        AppLogger.i(TAG, "📱 ManicTime服务创建")
        instance = this
        
        prefs = ManicTimePreferences(this)
        apiClient = ManicTimeApiClient(prefs)
        screenshotUploader = ScreenshotUploader(prefs)
        
        createNotificationChannel()
        isRunning = true
        AppLogger.i(TAG, "✅ 服务初始化完成")
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
            ACTION_MANUAL_UPLOAD -> {
                // 手动触发上传
                serviceScope.launch {
                    try {
                        Log.d(TAG, "手动触发上传")
                        uploadPendingData()
                    } catch (e: Exception) {
                        Log.e(TAG, "手动上传失败", e)
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
        AppLogger.i(TAG, "🚀 开始监控服务")
        
        // 初始化timeline
        serviceScope.launch {
            try {
                AppLogger.i(TAG, "📊 获取Timeline...")
                val (key, changeId, envId) = apiClient.getOrCreateTimeline()
                timelineKey = key
                lastChangeId = changeId
                environmentId = envId
                Log.d(TAG, "Timeline Key: $timelineKey, LastChangeId: $lastChangeId, EnvId: $environmentId")
                AppLogger.i(TAG, "✅ Timeline获取成功: $timelineKey")
            } catch (e: Exception) {
                Log.e(TAG, "获取timeline失败", e)
                AppLogger.e(TAG, "❌ 获取Timeline失败: ${e.message}", e)
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
                    
                    if (lastActiveApp != null && lastActivityTime > 0) {
                        // 保存上一个活动
                        val duration = (currentTime - lastActivityTime) / 1000 // 秒
                        if (duration > 0) {
                            // 获取上一个应用的名称
                            val prevAppName = try {
                                val pm = packageManager
                                val appInfo = pm.getApplicationInfo(lastActiveApp!!, 0)
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                lastActiveApp!!
                            }
                            
                            activityQueue.add(
                                ActivityRecord(
                                    appName = prevAppName,
                                    packageName = lastActiveApp!!,
                                    startTime = lastActivityTime,
                                    duration = duration
                                )
                            )
                            Log.d(TAG, "✅ 记录活动: $prevAppName (${lastActiveApp}), 时长: ${duration}秒, 队列: ${activityQueue.size}")
                        }
                    }
                    
                    lastActiveApp = packageName  // 保存packageName而不是appName
                    lastActivityTime = currentTime
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查当前活动失败", e)
        }
    }
    
    private suspend fun uploadPendingData() = withContext(Dispatchers.IO) {
        Log.d(TAG, "uploadPendingData 开始 - timelineKey: $timelineKey, activityQueue: ${activityQueue.size}, screenshotQueue: ${screenshotQueue.size}")
        AppLogger.i(TAG, "📤 开始上传数据 - Timeline: $timelineKey, 活动: ${activityQueue.size}, 截图: ${screenshotQueue.size}")
        
        if (timelineKey == null) {
            Log.w(TAG, "Timeline未初始化,跳过上传")
            AppLogger.w(TAG, "⚠️ Timeline未初始化，跳过上传")
            return@withContext
        }
        
        // 上传活动记录
        if (activityQueue.isNotEmpty()) {
            val activities = activityQueue.toList()
            val currentTimelineKey = timelineKey
            val currentLastChangeId = lastChangeId
            val currentEnvironmentId = environmentId
            try {
                Log.d(TAG, "准备上传 ${activities.size} 条活动记录")
                AppLogger.i(TAG, "📊 上传 ${activities.size} 条活动记录...")
                activityQueue.clear()
                
                // 批量上传（使用changes API）
                apiClient.uploadActivities(currentTimelineKey!!, currentLastChangeId, currentEnvironmentId!!, activities)
                
                Log.d(TAG, "✅ 成功上传了 ${activities.size} 条活动记录")
                AppLogger.i(TAG, "✅ 活动上传成功: ${activities.size} 条")
                updateNotification("已同步 ${activities.size} 条活动")
                
                // 记录上报时间
                prefs.setLastReportTime("applications", System.currentTimeMillis())
                Log.d(TAG, "已记录applications上报时间")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 上传活动失败: ${e.message}", e)
                AppLogger.e(TAG, "❌ 活动上传失败: ${e.message}", e)
                // 失败则放回队列
                activityQueue.addAll(0, activities)
            }
        } else {
            Log.d(TAG, "活动队列为空，跳过")
        }
        
        // 上传截图（通过SFTP）
        if (screenshotQueue.isNotEmpty()) {
            try {
                val screenshots = screenshotQueue.take(3).toList() // 一次最多上传3张
                Log.d(TAG, "准备上传 ${screenshots.size} 张截图")
                
                var successCount = 0
                for (screenshot in screenshots) {
                    Log.d(TAG, "上传截图: timestamp=${screenshot.timestamp}")
                    
                    // 从文件路径读取并上传
                    val file = if (screenshot.originalPath != null) {
                        File(screenshot.originalPath)
                    } else {
                        null
                    }
                    
                    if (file != null && file.exists()) {
                        val success = screenshotUploader.uploadScreenshot(file, screenshot.timestamp)
                        if (success) {
                            successCount++
                            screenshotQueue.remove(screenshot)
                            // 删除本地文件
                            file.delete()
                        }
                    } else {
                        Log.w(TAG, "截图文件不存在，从队列移除")
                        screenshotQueue.remove(screenshot)
                    }
                }
                
                if (successCount > 0) {
                    Log.d(TAG, "✅ 成功上传了 $successCount 张截图")
                    updateNotification("已上传 $successCount 张截图")
                    
                    // 记录上报时间
                    prefs.setLastReportTime("screenshots", System.currentTimeMillis())
                    Log.d(TAG, "已记录screenshots上报时间")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 上传截图失败: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "截图队列为空，跳过")
        }
        
        Log.d(TAG, "uploadPendingData 完成")
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
                    
                    // 加入上传队列
                    val screenshotData = ScreenshotData(
                        timestamp = System.currentTimeMillis(),
                        originalPath = originalFile.absolutePath,
                        thumbnailPath = thumbnailFile.absolutePath
                    )
                    screenshotQueue.add(screenshotData)
                    
                    Log.d(TAG, "截图已保存并加入队列: ${originalFile.name}, 队列大小: ${screenshotQueue.size}")
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
    
    /**
     * 设备状态变化回调（从DeviceStateReceiver调用）
     */
    fun onDeviceStateChanged(state: DeviceState) {
        val currentTime = System.currentTimeMillis()
        
        // 记录上一个状态的持续时间
        if (lastDeviceState != null && deviceStateStartTime > 0) {
            val duration = (currentTime - deviceStateStartTime) / 1000
            if (duration > 0) {
                // TODO: 上传到Computer usage timeline
                Log.d(TAG, "设备状态: $lastDeviceState, 持续: ${duration}秒")
            }
        }
        
        lastDeviceState = state
        deviceStateStartTime = currentTime
    }
    
    /**
     * 文档变化回调（从AccessibilityService调用）
     */
    fun onDocumentChanged(packageName: String, title: String?, url: String?) {
        val currentTime = System.currentTimeMillis()
        
        val document = DocumentRecord(
            packageName = packageName,
            title = title ?: "",
            url = url,
            timestamp = currentTime
        )
        
        // 避免重复记录
        if (document != lastDocument) {
            lastDocument = document
            documentsQueue.add(document)
            Log.d(TAG, "文档变化: $packageName - $title - $url")
        }
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
    val imageData: ByteArray? = null,
    val originalPath: String? = null,
    val thumbnailPath: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ScreenshotData
        return timestamp == other.timestamp
    }
    
    override fun hashCode(): Int = timestamp.hashCode()
}

data class DocumentRecord(
    val packageName: String,
    val title: String,
    val url: String?,
    val timestamp: Long
)
