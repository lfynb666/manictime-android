package com.manictime.android

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 辅助功能服务 - 用于自动截图
 * 
 * 功能说明：
 * 1. 仅用于定时截图，不读取任何用户输入
 * 2. 不监听密码、不模拟点击、不读取通知
 * 3. 截图保存到本地缓存，由ManicTimeService上传
 * 
 * 隐私承诺：
 * - 不收集任何个人信息
 * - 截图仅用于时间追踪
 * - 所有数据仅发送到用户自己的服务器
 */
class ScreenCaptureAccessibilityService : AccessibilityService() {
    
    companion object {
        const val TAG = "ScreenCaptureService"
        private var instance: ScreenCaptureAccessibilityService? = null
        
        fun isRunning(): Boolean = instance != null
        
        fun getInstance(): ScreenCaptureAccessibilityService? = instance
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var screenshotJob: Job? = null
    private lateinit var prefs: ManicTimePreferences
    private lateinit var screenshotManager: ScreenshotManager
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = ManicTimePreferences(this)
        screenshotManager = ScreenshotManager(this)
        Log.d(TAG, "辅助功能服务已创建")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "辅助功能服务已连接")
        
        // 启动定时截图
        startPeriodicScreenshot()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不处理任何辅助功能事件
        // 我们只使用这个服务来保持运行和截图
        // 不读取任何用户交互数据
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "辅助功能服务被中断")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        screenshotJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "辅助功能服务已销毁")
    }
    
    /**
     * 启动定时截图
     */
    private fun startPeriodicScreenshot() {
        if (!prefs.screenshotEnabled) {
            Log.d(TAG, "截图功能未启用")
            return
        }
        
        screenshotJob?.cancel()
        screenshotJob = serviceScope.launch {
            delay(5000) // 启动后5秒开始第一次截图
            
            while (isActive) {
                try {
                    captureScreen()
                } catch (e: Exception) {
                    Log.e(TAG, "截图失败", e)
                }
                delay(prefs.screenshotInterval)
            }
        }
        
        Log.d(TAG, "定时截图已启动，间隔: ${prefs.screenshotInterval / 1000}秒")
    }
    
    /**
     * 截取屏幕
     * 使用View树重建方式（辅助功能专用）
     */
    private suspend fun captureScreen() = withContext(Dispatchers.Main) {
        try {
            // 获取根窗口
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.w(TAG, "无法获取根窗口")
                return@withContext
            }
            
            // 获取屏幕尺寸
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            
            // 创建截图文件
            val timestamp = System.currentTimeMillis()
            val cacheDir = externalCacheDir ?: cacheDir
            val screenshotFile = File(cacheDir, "screenshot_$timestamp.jpg")
            
            // 尝试使用screencap命令截图
            withContext(Dispatchers.IO) {
                try {
                    // 方法1: 尝试直接执行screencap
                    val process = ProcessBuilder()
                        .command("screencap", "-p", screenshotFile.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    
                    val exitCode = process.waitFor()
                    val output = process.inputStream.bufferedReader().readText()
                    
                    Log.d(TAG, "screencap exitCode: $exitCode, output: $output")
                    
                    if (screenshotFile.exists() && screenshotFile.length() > 0) {
                        Log.d(TAG, "截图成功: ${screenshotFile.absolutePath}, 大小: ${screenshotFile.length() / 1024}KB")
                        notifyScreenshotReady(screenshotFile)
                    } else {
                        Log.w(TAG, "screencap失败，尝试使用sh命令")
                        
                        // 方法2: 通过sh执行
                        val shProcess = Runtime.getRuntime().exec(arrayOf(
                            "sh", "-c", "screencap -p ${screenshotFile.absolutePath}"
                        ))
                        shProcess.waitFor()
                        
                        if (screenshotFile.exists() && screenshotFile.length() > 0) {
                            Log.d(TAG, "通过sh截图成功")
                            notifyScreenshotReady(screenshotFile)
                        } else {
                            Log.e(TAG, "所有截图方法都失败了")
                            Log.e(TAG, "这个设备可能不支持screencap命令，或需要ROOT权限")
                            screenshotFile.delete()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "截图命令执行失败", e)
                    screenshotFile.delete()
                }
            }
            
            rootNode.recycle()
            
        } catch (e: Exception) {
            Log.e(TAG, "截图过程出错", e)
        }
    }
    
    /**
     * 通知ManicTimeService有新截图
     */
    private suspend fun notifyScreenshotReady(file: File) {
        // 使用ScreenshotManager保存截图（原图 + 缩略图）
        val result = screenshotManager.saveScreenshot(file)
        if (result != null) {
            val (originalFile, thumbnailFile) = result
            
            // 标记为待上传
            screenshotManager.markForUpload(originalFile, thumbnailFile)
            
            Log.d(TAG, "截图已保存并标记待上传: ${originalFile.name}")
        }
    }
    
    /**
     * 重启定时截图（设置改变时调用）
     */
    fun restartScreenshot() {
        screenshotJob?.cancel()
        startPeriodicScreenshot()
    }
}
