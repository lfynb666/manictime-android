package com.manictime.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 截图管理器
 * 负责截图的保存、压缩和管理
 */
class ScreenshotManager(private val context: Context) {
    
    companion object {
        const val TAG = "ScreenshotManager"
        private const val THUMBNAIL_QUALITY = 50 // 缩略图质量
        private const val THUMBNAIL_SCALE = 0.3f // 缩略图缩放比例
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_Z", Locale.US)
    
    /**
     * 获取截图存储目录
     * 使用私有目录，不会被系统图库扫描
     */
    fun getScreenshotsDir(): File {
        // 使用外部存储的私有目录，卸载时会被删除
        // 如果需要永久保存，可以改为 Environment.getExternalStoragePublicDirectory
        val dir = File(context.getExternalFilesDir(null), "ManicTime/Screenshots")
        if (!dir.exists()) {
            dir.mkdirs()
            // 创建 .nomedia 文件，防止被系统图库扫描
            File(dir, ".nomedia").createNewFile()
        }
        return dir
    }
    
    /**
     * 获取待上传目录
     */
    fun getPendingUploadDir(): File {
        val dir = File(context.getExternalFilesDir(null), "ManicTime/Pending")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * 生成截图文件名
     * 
     * ManicTime标准格式: Screenshot_YYYY-MM-DD_HH-MM-SS.jpg
     * 例如: Screenshot_2025-11-20_18-30-45.jpg
     * 
     * 关键点:
     * 1. 文件名本身不重要，Server主要读取EXIF时间戳
     * 2. 但为了兼容性和可读性，使用标准格式
     * 3. 时间使用本地时区
     * 4. 缩略图添加 .thumbnail 后缀
     */
    fun generateScreenshotFileName(): String {
        val timestamp = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val dateStr = sdf.format(Date(timestamp))
        return "Screenshot_$dateStr.jpg"
    }
    
    /**
     * 保存截图（原图 + 缩略图）
     * @return Pair<原图文件, 缩略图文件>
     */
    suspend fun saveScreenshot(screenshotFile: File): Pair<File, File>? = withContext(Dispatchers.IO) {
        try {
            if (!screenshotFile.exists() || screenshotFile.length() == 0L) {
                Log.w(TAG, "截图文件不存在或为空")
                return@withContext null
            }
            
            // 读取原图
            val originalBitmap = BitmapFactory.decodeFile(screenshotFile.absolutePath)
            if (originalBitmap == null) {
                Log.w(TAG, "无法解码截图文件")
                screenshotFile.delete()
                return@withContext null
            }
            
            val width = originalBitmap.width
            val height = originalBitmap.height
            
            // 生成文件名
            val fileName = generateScreenshotFileName()
            val screenshotsDir = getScreenshotsDir()
            
            // 保存原图
            val originalFile = File(screenshotsDir, fileName)
            FileOutputStream(originalFile).use { out ->
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Log.d(TAG, "原图已保存: ${originalFile.absolutePath}, 大小: ${originalFile.length() / 1024}KB")
            
            // 生成缩略图
            val thumbnailWidth = (width * THUMBNAIL_SCALE).toInt()
            val thumbnailHeight = (height * THUMBNAIL_SCALE).toInt()
            val thumbnailBitmap = Bitmap.createScaledBitmap(
                originalBitmap,
                thumbnailWidth,
                thumbnailHeight,
                true
            )
            
            // 保存缩略图
            val thumbnailFileName = fileName.replace(".jpg", ".thumbnail.jpg")
            val thumbnailFile = File(screenshotsDir, thumbnailFileName)
            FileOutputStream(thumbnailFile).use { out ->
                thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
            }
            Log.d(TAG, "缩略图已保存: ${thumbnailFile.absolutePath}, 大小: ${thumbnailFile.length() / 1024}KB")
            
            // 释放资源
            originalBitmap.recycle()
            thumbnailBitmap.recycle()
            
            // 删除临时文件
            screenshotFile.delete()
            
            Pair(originalFile, thumbnailFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "保存截图失败", e)
            null
        }
    }
    
    /**
     * 标记截图为待上传
     * 将截图信息写入待上传目录
     */
    suspend fun markForUpload(originalFile: File, thumbnailFile: File) = withContext(Dispatchers.IO) {
        try {
            val pendingDir = getPendingUploadDir()
            
            // 创建标记文件，记录需要上传的截图
            val markerFile = File(pendingDir, "${originalFile.name}.marker")
            markerFile.writeText("${originalFile.absolutePath}\n${thumbnailFile.absolutePath}")
            
            Log.d(TAG, "截图已标记为待上传: ${originalFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "标记上传失败", e)
        }
    }
    
    /**
     * 获取所有待上传的截图
     */
    suspend fun getPendingScreenshots(): List<Pair<File, File>> = withContext(Dispatchers.IO) {
        try {
            val pendingDir = getPendingUploadDir()
            val markers = pendingDir.listFiles { file ->
                file.name.endsWith(".marker")
            } ?: return@withContext emptyList()
            
            markers.mapNotNull { marker ->
                try {
                    val lines = marker.readLines()
                    if (lines.size >= 2) {
                        val original = File(lines[0])
                        val thumbnail = File(lines[1])
                        if (original.exists() && thumbnail.exists()) {
                            Pair(original, thumbnail)
                        } else {
                            // 文件不存在，删除标记
                            marker.delete()
                            null
                        }
                    } else {
                        marker.delete()
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "读取标记文件失败: ${marker.name}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取待上传截图失败", e)
            emptyList()
        }
    }
    
    /**
     * 移除上传标记（上传成功后调用）
     */
    suspend fun removeUploadMarker(originalFile: File) = withContext(Dispatchers.IO) {
        try {
            val pendingDir = getPendingUploadDir()
            val markerFile = File(pendingDir, "${originalFile.name}.marker")
            if (markerFile.exists()) {
                markerFile.delete()
                Log.d(TAG, "已移除上传标记: ${originalFile.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除标记失败", e)
        }
    }
    
    /**
     * 获取所有截图文件（用于用户查看）
     */
    fun getAllScreenshots(): List<File> {
        val screenshotsDir = getScreenshotsDir()
        return screenshotsDir.listFiles { file ->
            file.name.endsWith(".jpg") && !file.name.contains(".thumbnail.")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    /**
     * 获取截图统计信息
     */
    fun getStatistics(): ScreenshotStatistics {
        val screenshotsDir = getScreenshotsDir()
        val allFiles = screenshotsDir.listFiles() ?: emptyArray()
        
        val originalFiles = allFiles.filter { it.name.endsWith(".jpg") && !it.name.contains(".thumbnail.") }
        val thumbnailFiles = allFiles.filter { it.name.endsWith(".thumbnail.jpg") }
        
        val totalSize = allFiles.sumOf { it.length() }
        val originalSize = originalFiles.sumOf { it.length() }
        val thumbnailSize = thumbnailFiles.sumOf { it.length() }
        
        return ScreenshotStatistics(
            totalCount = originalFiles.size,
            totalSize = totalSize,
            originalSize = originalSize,
            thumbnailSize = thumbnailSize,
            storageDir = screenshotsDir.absolutePath
        )
    }
}

/**
 * 截图统计信息
 */
data class ScreenshotStatistics(
    val totalCount: Int,
    val totalSize: Long,
    val originalSize: Long,
    val thumbnailSize: Long,
    val storageDir: String
)
