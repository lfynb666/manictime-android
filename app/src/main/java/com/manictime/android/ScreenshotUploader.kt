package com.manictime.android

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * 截图上传工具 - 通过HTTP API上传到服务器
 */
class ScreenshotUploader(
    private val prefs: ManicTimePreferences
) {
    companion object {
        const val TAG = "ScreenshotUploader"
        private const val UPLOAD_PORT = 8888
        private const val UPLOAD_TOKEN = "kXgZGAQyVVyrwwlgRSi2RjfT-8mOpSga8EvRqzAucXw"
        
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
            
            // 生成文件名
            val dateStr = dateFormat.format(date)
            val timeStr = timeFormat.format(date)
            val timezoneStr = timezoneFormat.format(date).replace(":", "-")  // +08:00 -> +08-00
            val width = bitmap.width
            val height = bitmap.height
            val uniqueId = System.currentTimeMillis() % 1000000  // 6位唯一ID
            val flag = 0
            
            val filename = "${dateStr}_${timeStr}_${timezoneStr}_${width}_${height}_${uniqueId}_${flag}.jpg"
            val thumbnailFilename = "${dateStr}_${timeStr}_${timezoneStr}_${width}_${height}_${uniqueId}_${flag}.thumbnail.jpg"
            
            Log.d(TAG, "准备上传截图: $filename")
            
            // 压缩图片
            val fullImage = compressBitmap(bitmap, 85)
            val thumbnail = createThumbnail(bitmap)
            
            // 上传到服务器
            uploadToHTTP(deviceUUID, timestamp, dateStr, filename, fullImage)
            uploadToHTTP(deviceUUID, timestamp, dateStr, thumbnailFilename, thumbnail)
            
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
     * 通过HTTP API上传文件
     */
    private fun uploadToHTTP(
        deviceUUID: String,
        timestamp: Long,
        dateStr: String,
        filename: String,
        data: ByteArray
    ) {
        // 从serverUrl提取主机名
        val host = prefs.serverUrl
            .replace("http://", "")
            .replace("https://", "")
            .split(":")[0]
        
        val url = URL("http://$host:$UPLOAD_PORT/upload")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $UPLOAD_TOKEN")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            // 构建JSON请求
            val base64Data = Base64.encodeToString(data, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("deviceUUID", deviceUUID)
                put("timestamp", timestamp)
                put("date", dateStr)
                put("filename", filename)
                put("imageData", base64Data)
            }
            
            // 发送请求
            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(json.toString())
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "HTTP上传成功: $filename (${data.size / 1024}KB)")
            } else {
                val error = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                throw Exception("HTTP $responseCode: $error")
            }
        } finally {
            connection.disconnect()
        }
    }
}
