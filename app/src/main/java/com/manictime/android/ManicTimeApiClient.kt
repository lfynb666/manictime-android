package com.manictime.android

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

/**
 * ManicTime API客户端
 * 实现与ManicTime Server的所有API交互
 */
class ManicTimeApiClient(private val prefs: ManicTimePreferences) {
    companion object {
        const val TAG = "ManicTimeApiClient"
        const val ACCEPT_HEADER = "application/vnd.manictime.v3+json"
        const val CONTENT_TYPE_JSON = "application/vnd.manictime.v3+json"
        const val CONTENT_TYPE_FORM = "application/x-www-form-urlencoded"
        
        // ISO 8601日期格式
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }
    
    /**
     * 认证并获取访问令牌
     */
    suspend fun authenticate(): String = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl}/api/token"
        
        val params = "grant_type=password" +
                "&username=${URLEncoder.encode(prefs.username, "UTF-8")}" +
                "&password=${URLEncoder.encode(prefs.password, "UTF-8")}"
        
        val response = post(url, params, CONTENT_TYPE_FORM, useAuth = false)
        val json = JSONObject(response)
        
        json.getString("token")
    }
    
    /**
     * 获取或创建当前设备的Timeline
     */
    suspend fun getOrCreateTimeline(): String = withContext(Dispatchers.IO) {
        // 1. 获取所有timeline
        val timelinesUrl = "${prefs.serverUrl}/api/timelines"
        val response = get(timelinesUrl)
        val json = JSONObject(response)
        
        val timelines = json.getJSONArray("timelines")
        val deviceName = "Android-${android.os.Build.MODEL}"
        
        // 2. 查找标签timeline
        for (i in 0 until timelines.length()) {
            val timeline = timelines.getJSONObject(i)
            val schema = timeline.getJSONObject("schema")
            val schemaName = schema.getString("name")
            
            // 寻找Tags类型的timeline
            if (schemaName == "ManicTime/Tags") {
                val timelineKey = timeline.getString("timelineKey")
                Log.d(TAG, "找到Tags timeline: $timelineKey")
                return@withContext timelineKey
            }
        }
        
        // 如果没有找到,使用第一个timeline
        if (timelines.length() > 0) {
            val firstTimeline = timelines.getJSONObject(0)
            val timelineKey = firstTimeline.getString("timelineKey")
            Log.d(TAG, "使用第一个timeline: $timelineKey")
            return@withContext timelineKey
        }
        
        throw Exception("未找到可用的Timeline")
    }
    
    /**
     * 上传活动记录
     */
    suspend fun uploadActivity(
        timelineKey: String,
        activity: ActivityRecord
    ) = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl}/api/timelines/$timelineKey/activities"
        
        val startTime = dateFormat.format(Date(activity.startTime))
        
        val json = JSONObject().apply {
            put("values", JSONObject().apply {
                put("name", activity.appName)
                put("notes", "Android应用: ${activity.packageName}")
                put("timeInterval", JSONObject().apply {
                    put("start", startTime)
                    put("duration", activity.duration.toInt())
                })
            })
        }
        
        Log.d(TAG, "上传活动: ${json.toString(2)}")
        post(url, json.toString(), CONTENT_TYPE_JSON)
    }
    
    /**
     * 上传截图
     */
    suspend fun uploadScreenshot(screenshot: ScreenshotData) = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl}/api/screenshots"
        
        val base64Image = Base64.encodeToString(screenshot.imageData, Base64.NO_WRAP)
        val timestamp = dateFormat.format(Date(screenshot.timestamp))
        
        val json = JSONObject().apply {
            put("capturedTime", timestamp)
            put("imageData", base64Image)
            put("imageFormat", "jpeg")
            put("deviceName", "Android-${android.os.Build.MODEL}")
        }
        
        Log.d(TAG, "上传截图: ${screenshot.imageData.size / 1024}KB at $timestamp")
        post(url, json.toString(), CONTENT_TYPE_JSON)
    }
    
    /**
     * 创建标签活动
     */
    suspend fun createTag(
        timelineKey: String,
        tagName: String,
        startTime: Long,
        duration: Long,
        notes: String? = null
    ) = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl}/api/timelines/$timelineKey/activities"
        
        val startTimeStr = dateFormat.format(Date(startTime))
        
        val json = JSONObject().apply {
            put("values", JSONObject().apply {
                put("name", tagName)
                if (notes != null) {
                    put("notes", notes)
                }
                put("timeInterval", JSONObject().apply {
                    put("start", startTimeStr)
                    put("duration", duration.toInt())
                })
            })
        }
        
        post(url, json.toString(), CONTENT_TYPE_JSON)
    }
    
    /**
     * 获取活动列表
     */
    suspend fun getActivities(
        timelineKey: String,
        fromTime: Date,
        toTime: Date
    ): List<Activity> = withContext(Dispatchers.IO) {
        val fromStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(fromTime)
        val toStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(toTime)
        
        val url = "${prefs.serverUrl}/api/timelines/$timelineKey/activities" +
                "?fromTime=$fromStr&toTime=$toStr"
        
        val response = get(url)
        val json = JSONObject(response)
        
        val activities = mutableListOf<Activity>()
        val activitiesArray = json.optJSONArray("activities")
        
        if (activitiesArray != null) {
            for (i in 0 until activitiesArray.length()) {
                val activityObj = activitiesArray.getJSONObject(i)
                val values = activityObj.getJSONObject("values")
                val interval = values.getJSONObject("timeInterval")
                
                activities.add(Activity(
                    name = values.optString("name", ""),
                    notes = values.optString("notes", null),
                    start = interval.getString("start"),
                    duration = interval.getInt("duration")
                ))
            }
        }
        
        activities
    }
    
    /**
     * 获取允许的标签组合
     */
    suspend fun getAllowedTags(): List<String> = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl}/api/tagcombinationlist"
        
        val response = get(url)
        val json = JSONObject(response)
        
        val tags = mutableListOf<String>()
        val tagArray = json.optJSONArray("tagCombinations")
        
        if (tagArray != null) {
            for (i in 0 until tagArray.length()) {
                tags.add(tagArray.getString(i))
            }
        }
        
        tags
    }
    
    // ========== HTTP辅助方法 ==========
    
    private fun get(
        urlString: String,
        useAuth: Boolean = true
    ): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", ACCEPT_HEADER)
            
            if (useAuth && prefs.accessToken.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer ${prefs.accessToken}")
            }
            
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            val responseCode = connection.responseCode
            Log.d(TAG, "GET $urlString -> $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return readResponse(connection)
            } else {
                val error = readErrorResponse(connection)
                throw Exception("HTTP $responseCode: $error")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    private fun post(
        urlString: String,
        body: String,
        contentType: String,
        useAuth: Boolean = true
    ): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Accept", ACCEPT_HEADER)
            connection.setRequestProperty("Content-Type", contentType)
            
            if (useAuth && prefs.accessToken.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer ${prefs.accessToken}")
            }
            
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            // 写入请求体
            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(body)
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "POST $urlString -> $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK || 
                responseCode == HttpURLConnection.HTTP_CREATED) {
                return readResponse(connection)
            } else {
                val error = readErrorResponse(connection)
                throw Exception("HTTP $responseCode: $error")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    private fun readResponse(connection: HttpURLConnection): String {
        val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
        return reader.use { it.readText() }
    }
    
    private fun readErrorResponse(connection: HttpURLConnection): String {
        return try {
            val reader = BufferedReader(InputStreamReader(connection.errorStream, "UTF-8"))
            reader.use { it.readText() }
        } catch (e: Exception) {
            connection.responseMessage ?: "Unknown error"
        }
    }
}

// 数据类
data class Activity(
    val name: String,
    val notes: String?,
    val start: String,
    val duration: Int
)

data class Timeline(
    val timelineKey: String,
    val owner: String,
    val deviceName: String,
    val schemaName: String
)