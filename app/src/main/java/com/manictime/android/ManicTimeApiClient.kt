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
        
        Log.d(TAG, "认证请求: $url")
        
        try {
            val response = post(url, params, CONTENT_TYPE_FORM, useAuth = false)
            Log.d(TAG, "认证响应: $response")
            
            val json = JSONObject(response)
            
            // ManicTime Server返回的是access_token，不是token
            val token = if (json.has("access_token")) {
                json.getString("access_token")
            } else if (json.has("token")) {
                json.getString("token")
            } else {
                throw Exception("响应中没有找到token字段")
            }
            
            Log.d(TAG, "认证成功，token长度: ${token.length}")
            token
        } catch (e: Exception) {
            Log.e(TAG, "认证失败", e)
            throw Exception("认证失败: ${e.message}")
        }
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
        
        // 2. 打印所有timeline类型
        Log.d(TAG, "=== 可用的Timeline列表 ===")
        AppLogger.i(TAG, "📋 可用的Timeline列表:")
        for (i in 0 until timelines.length()) {
            val timeline = timelines.getJSONObject(i)
            val schema = timeline.getJSONObject("schema")
            val schemaName = schema.getString("name")
            val timelineKey = timeline.getString("timelineKey")
            Log.d(TAG, "Timeline $i: $schemaName -> $timelineKey")
            AppLogger.i(TAG, "  [$i] $schemaName -> $timelineKey")
        }
        
        // 3. 查找Applications timeline (用于应用使用记录)
        for (i in 0 until timelines.length()) {
            val timeline = timelines.getJSONObject(i)
            val schema = timeline.getJSONObject("schema")
            val schemaName = schema.getString("name")
            
            // 优先查找Applications类型的timeline
            if (schemaName == "ManicTime/Applications") {
                val timelineKey = timeline.getString("timelineKey")
                Log.d(TAG, "✅ 使用Applications timeline: $timelineKey")
                return@withContext timelineKey
            }
        }
        
        // 4. 如果没有Applications，查找Computer usage
        for (i in 0 until timelines.length()) {
            val timeline = timelines.getJSONObject(i)
            val schema = timeline.getJSONObject("schema")
            val schemaName = schema.getString("name")
            
            if (schemaName.contains("Computer usage", ignoreCase = true)) {
                val timelineKey = timeline.getString("timelineKey")
                Log.d(TAG, "✅ 使用Computer Usage timeline: $timelineKey")
                return@withContext timelineKey
            }
        }
        
        // 如果没有找到Computer Usage，再找Tags
        for (i in 0 until timelines.length()) {
            val timeline = timelines.getJSONObject(i)
            val schema = timeline.getJSONObject("schema")
            val schemaName = schema.getString("name")
            
            if (schemaName == "ManicTime/Tags") {
                val timelineKey = timeline.getString("timelineKey")
                Log.d(TAG, "找到Tags timeline: $timelineKey")
                return@withContext timelineKey
            }
        }
        
        // 如果都没有找到,使用第一个timeline
        if (timelines.length() > 0) {
            val firstTimeline = timelines.getJSONObject(0)
            val timelineKey = firstTimeline.getString("timelineKey")
            val schema = firstTimeline.getJSONObject("schema")
            val schemaName = schema.getString("name")
            Log.d(TAG, "使用第一个timeline: $timelineKey (类型: $schemaName)")
            return@withContext timelineKey
        }
        
        throw Exception("未找到可用的Timeline")
    }
    
    /**
     * 批量上传活动记录（使用activityupdates API）
     */
    suspend fun uploadActivities(
        timelineKey: String,
        activities: List<ActivityRecord>
    ) = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl}/api/timelines/$timelineKey/activityupdates"
        
        // 构建ClientEnvironment
        val clientEnvironment = JSONObject().apply {
            put("applicationName", "ManicTime Android")
            put("applicationVersion", "1.0.0")
            put("databaseId", "android-${android.os.Build.MODEL}")
            put("deviceName", "Android-${android.os.Build.MODEL}")
            put("operatingSystem", "Android")
            put("operatingSystemVersion", android.os.Build.VERSION.RELEASE)
        }
        
        // 构建groups（按packageName去重）
        val groupsMap = mutableMapOf<String, String>()
        activities.forEach { activity ->
            groupsMap[activity.packageName] = activity.appName
        }
        
        val groupsArray = JSONArray()
        groupsMap.forEach { (packageName, appName) ->
            groupsArray.put(JSONObject().apply {
                put("groupId", packageName)
                put("displayName", appName)
                put("displayKey", packageName)
                put("color", generateColorForPackage(packageName))
                put("skipColor", false)
            })
        }
        
        // 构建activities
        val activitiesArray = JSONArray()
        activities.forEachIndexed { index, activity ->
            val startTime = dateFormat.format(Date(activity.startTime))
            val endTime = dateFormat.format(Date(activity.startTime + activity.duration * 1000))
            
            activitiesArray.put(JSONObject().apply {
                put("activityId", "android_${activity.startTime}_$index")
                put("displayName", activity.appName)
                put("groupId", activity.packageName)
                put("startTime", startTime)
                put("endTime", endTime)
            })
        }
        
        val json = JSONObject().apply {
            put("clientEnvironment", clientEnvironment)
            put("timelineKey", timelineKey)
            put("groups", groupsArray)
            put("activities", activitiesArray)
        }
        
        Log.d(TAG, "上传 ${activities.size} 条活动记录")
        Log.d(TAG, "请求体: ${json.toString(2)}")
        AppLogger.i(TAG, "📤 上传URL: $url")
        AppLogger.i(TAG, "📦 请求体大小: ${json.toString().length} 字节")
        AppLogger.i(TAG, "📝 请求体内容:\n${json.toString(2)}")
        
        try {
            post(url, json.toString(), CONTENT_TYPE_JSON)
            AppLogger.i(TAG, "✅ 活动上传API调用成功")
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ 活动上传API失败", e)
            throw e
        }
    }
    
    /**
     * 为包名生成颜色
     */
    private fun generateColorForPackage(packageName: String): String {
        val hash = packageName.hashCode()
        val r = (hash and 0xFF0000) shr 16
        val g = (hash and 0x00FF00) shr 8
        val b = hash and 0x0000FF
        return String.format("%02X%02X%02X", r, g, b)
    }
    
    // 截图上传已移至 ScreenshotUploader.kt，通过SFTP直接上传到服务器文件系统
    
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
            val responseMessage = connection.responseMessage
            Log.d(TAG, "POST $urlString -> $responseCode $responseMessage")
            AppLogger.i(TAG, "📡 响应状态: $responseCode $responseMessage")
            
            if (responseCode == HttpURLConnection.HTTP_OK || 
                responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = readResponse(connection)
                AppLogger.i(TAG, "✅ 响应成功，长度: ${response.length}")
                return response
            } else {
                val error = readErrorResponse(connection)
                Log.e(TAG, "POST失败 $responseCode: $error")
                AppLogger.e(TAG, "❌ HTTP $responseCode: $responseMessage")
                AppLogger.e(TAG, "📄 错误响应: ${error.take(500)}") // 只取前500字符
                
                // 特殊处理502错误
                if (responseCode == HttpURLConnection.HTTP_BAD_GATEWAY) {
                    throw Exception("服务器网关错误(502)，请检查ManicTime Server是否正常运行")
                }
                
                throw Exception("HTTP $responseCode $responseMessage: ${error.take(200)}")
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