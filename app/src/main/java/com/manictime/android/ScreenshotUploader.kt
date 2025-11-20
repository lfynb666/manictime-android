package com.manictime.android

import android.graphics.Bitmap
import android.util.Log
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 截图上传工具 - 通过SFTP直接上传到服务器文件系统
 */
class ScreenshotUploader(
    private val prefs: ManicTimePreferences
) {
    companion object {
        const val TAG = "ScreenshotUploader"
        private const val SSH_PORT = 22
        private const val USER_ID = "1"  // ManicTime用户ID
        private const val BASE_PATH = "/root/ManicTimeServer/manictimeserver/Data/Screenshots"
        
        // 日期格式
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val timeFormat = SimpleDateFormat("HH-mm-ss", Locale.US)
        private val timezoneFormat = SimpleDateFormat("XXX", Locale.US)  // +08:00 格式
    }
    
    /**
     * 上传截图到服务器（从文件）
     */
    suspend fun uploadScreenshot(
        file: File,
        timestamp: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) {
                Log.e(TAG, "无法解码截图文件: ${file.absolutePath}")
                return@withContext false
            }
            val result = uploadScreenshotBitmap(bitmap, timestamp)
            bitmap.recycle()
            result
        } catch (e: Exception) {
            Log.e(TAG, "上传截图失败", e)
            false
        }
    }
    
    /**
     * 上传截图到服务器（从Bitmap）
     */
    private suspend fun uploadScreenshotBitmap(
        bitmap: Bitmap,
        timestamp: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val date = Date(timestamp)
            val deviceUUID = prefs.deviceUUID
            
            // 构建路径: /Screenshots/{userId}/{deviceUUID}/{date}/
            val dateStr = dateFormat.format(date)
            val remotePath = "$BASE_PATH/$USER_ID/$deviceUUID/$dateStr"
            
            // 生成文件名
            val timeStr = timeFormat.format(date)
            val timezoneStr = timezoneFormat.format(date).replace(":", "-")  // +08:00 -> +08-00
            val width = bitmap.width
            val height = bitmap.height
            val uniqueId = System.currentTimeMillis() % 1000000  // 6位唯一ID
            val flag = 0
            
            val filename = "${dateStr}_${timeStr}_${timezoneStr}_${width}_${height}_${uniqueId}_${flag}.jpg"
            val thumbnailFilename = "${dateStr}_${timeStr}_${timezoneStr}_${width}_${height}_${uniqueId}_${flag}.thumbnail.jpg"
            
            Log.d(TAG, "准备上传截图: $remotePath/$filename")
            
            // 压缩图片
            val fullImage = compressBitmap(bitmap, 85)
            val thumbnail = createThumbnail(bitmap)
            
            // 上传到服务器
            uploadToSFTP(remotePath, filename, fullImage)
            uploadToSFTP(remotePath, thumbnailFilename, thumbnail)
            
            Log.d(TAG, "✅ 截图上传成功: $filename")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 截图上传失败", e)
            false
        }
    }
    
    /**
     * 压缩Bitmap为JPEG字节数组
     */
    private fun compressBitmap(bitmap: Bitmap, quality: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }
    
    /**
     * 创建缩略图
     */
    private fun createThumbnail(bitmap: Bitmap): ByteArray {
        val maxSize = 200
        val ratio = Math.min(
            maxSize.toFloat() / bitmap.width,
            maxSize.toFloat() / bitmap.height
        )
        val width = (bitmap.width * ratio).toInt()
        val height = (bitmap.height * ratio).toInt()
        
        val thumbnail = Bitmap.createScaledBitmap(bitmap, width, height, true)
        return compressBitmap(thumbnail, 75)
    }
    
    /**
     * 通过SFTP上传文件
     */
    private fun uploadToSFTP(remotePath: String, filename: String, data: ByteArray) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        
        try {
            // 从serverUrl提取主机名
            val host = prefs.serverUrl
                .replace("http://", "")
                .replace("https://", "")
                .split(":")[0]
            
            // 创建SSH连接
            val jsch = JSch()
            session = jsch.getSession("root", host, SSH_PORT)
            session.setPassword(prefs.password)  // 使用ManicTime的密码
            
            // 跳过主机密钥检查（生产环境应该验证）
            val config = Properties()
            config["StrictHostKeyChecking"] = "no"
            session.setConfig(config)
            
            session.connect(10000)
            
            // 打开SFTP通道
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(5000)
            
            // 创建目录（如果不存在）
            createRemoteDirectory(channel, remotePath)
            
            // 上传文件
            channel.cd(remotePath)
            channel.put(ByteArrayInputStream(data), filename)
            
            Log.d(TAG, "SFTP上传成功: $remotePath/$filename (${data.size / 1024}KB)")
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }
    
    /**
     * 递归创建远程目录
     */
    private fun createRemoteDirectory(channel: ChannelSftp, path: String) {
        val dirs = path.split("/").filter { it.isNotEmpty() }
        var currentPath = ""
        
        for (dir in dirs) {
            currentPath += "/$dir"
            try {
                channel.cd(currentPath)
            } catch (e: Exception) {
                // 目录不存在，创建它
                channel.mkdir(currentPath)
                channel.cd(currentPath)
            }
        }
    }
}
