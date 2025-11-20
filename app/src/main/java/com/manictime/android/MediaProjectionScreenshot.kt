package com.manictime.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

class MediaProjectionScreenshot(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {
    companion object {
        const val TAG = "MPScreenshot"
    }
    
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    suspend fun captureScreen(): File? = suspendCancellableCoroutine { continuation ->
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi
            
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null, null
            )
            
            val handler = Handler(Looper.getMainLooper())
            imageReader!!.setOnImageAvailableListener({ reader ->
                var image: Image? = null
                var bitmap: Bitmap? = null
                try {
                    image = reader.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width
                        
                        bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        
                        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        
                        val timestamp = System.currentTimeMillis()
                        val file = File(context.cacheDir, "screenshot_$timestamp.jpg")
                        FileOutputStream(file).use { out ->
                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        
                        Log.d(TAG, "截图成功: ${file.absolutePath}")
                        
                        cleanup()
                        continuation.resume(file)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "截图失败", e)
                    cleanup()
                    continuation.resume(null)
                } finally {
                    image?.close()
                    bitmap?.recycle()
                }
            }, handler)
            
            continuation.invokeOnCancellation {
                cleanup()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化截图失败", e)
            cleanup()
            continuation.resume(null)
        }
    }
    
    private fun cleanup() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            virtualDisplay = null
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "清理资源失败", e)
        }
    }
}
