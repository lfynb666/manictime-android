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
        
        // ISO 8601日期格式 (不含毫秒，与ManicTime Server兼容)
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
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
     * 返回: Triple(timelineKey, lastChangeId, environmentId)
     */
    suspend fun getOrCreateTimeline(): Triple<String, String?, String> = withContext(Dispatchers.IO) {
        // 1. 获取所有timeline
        val timelinesUrl = "${prefs.serverUrl}/api/timelines"
        val response = get(timelinesUrl)
        val json = JSONObject(response)
        
        val timelines = json.getJSONArray("timelines")
        val currentDeviceName = android.os.Build.MODEL
        
        // 2. 打印所有timeline类型和links
        Log.d(TAG, "=== 可用的Timeline列表 ===")
        AppLogger.i(TAG, "可用的Timeline列表 (当前设备MODEL: '$currentDeviceName'):")
        for (i in 0 until timelines.length()) {
            val timeline = timelines.getJSONObject(i)
            val schema = timeline.getJSONObject("schema")
            val schemaName = schema.getString("name")
            val timelineKey = timeline.getString("timelineKey")
            val homeEnv = timeline.getJSONObject("homeEnvironment")
            val deviceName = homeEnv.getString("deviceName")
            Log.d(TAG, "Timeline $i: $schemaName -> $timelineKey (设备: $deviceName)")
            AppLogger.i(TAG, "  [$i] $schemaName -> $timelineKey (设备: '$deviceName')")
        }
        
        // 3. 优先查找当前设备的Applications timeline (使用包含匹配)
        for (i in 0 until timelines.length()) {
            val timeline = timelines.getJSONObject(i)
            val schema = timeline.getJSONObject("schema")
            val schemaName = schema.getString("name")
            val homeEnv = timeline.getJSONObject("homeEnvironment")
            val deviceName = homeEnv.getString("deviceName")
            
            // 使用包含匹配：deviceName包含currentDeviceName 或 currentDeviceName包含deviceName
            if (schemaName == "ManicTime/Applications" && 
                (deviceName.contains(currentDeviceName, ignoreCase = true) || 
                 currentDeviceName.contains(deviceName, ignoreCase = true))) {
                val timelineKey = timeline.getString("timelineKey")
                val lastChangeId = if (timeline.has("lastChangeId")) timeline.getString("lastChangeId") else null
                val environmentId = homeEnv.getString("environmentId")
                Log.d(TAG, "使用当前设备的Applications timeline: $timelineKey")
                AppLogger.i(TAG, "✅ 使用当前设备($currentDeviceName)的Applications timeline")
                return@withContext Triple(timelineKey, lastChangeId, environmentId)
            }
        }
        
        // 4. 如果没有当前设备的Applications，查找当前设备的ComputerUsage
        for (i in 0 until timelines.length()) {
            val timeline = timelines.getJSONObject(i)
            val schema = timeline.getJSONObject("schema")
            val schemaName = schema.getString("name")
            val homeEnv = timeline.getJSONObject("homeEnvironment")
            val deviceName = homeEnv.getString("deviceName")
            
            if (schemaName == "ManicTime/ComputerUsage" && 
                (deviceName.contains(currentDeviceName, ignoreCase = true) || 
                 currentDeviceName.contains(deviceName, ignoreCase = true))) {
                val timelineKey = timeline.getString("timelineKey")
                val lastChangeId = if (timeline.has("lastChangeId")) timeline.getString("lastChangeId") else null
                val environmentId = homeEnv.getString("environmentId")
                Log.d(TAG, "使用当前设备的ComputerUsage timeline: $timelineKey")
                AppLogger.i(TAG, "✅ 使用当前设备($currentDeviceName)的ComputerUsage timeline")
                return@withContext Triple(timelineKey, lastChangeId, environmentId)
            }
        }
        
        // 如果没有找到当前设备的timeline，再找Applications
        for (i in 0 until timelines.length()) {
            val timeline = timelines.getJSONObject(i)
            val schema = timeline.getJSONObject("schema")
            val schemaName = schema.getString("name")
            
            // 优先查找Applications类型的timeline
            if (schemaName == "ManicTime/Applications") {
                val timelineKey = timeline.getString("timelineKey")
                val lastChangeId = if (timeline.has("lastChangeId")) timeline.getString("lastChangeId") else null
                val homeEnv = timeline.getJSONObject("homeEnvironment")
                val environmentId = homeEnv.getString("environmentId")
                Log.d(TAG, "使用Applications timeline: $timelineKey, lastChangeId: $lastChangeId, envId: $environmentId")
                return@withContext Triple(timelineKey, lastChangeId, environmentId)
            }
        }
        
        // 4. 如果没有Applications，查找Computer usage
        for (i in 0 until timelines.length()) {
            val timeline = timelines.getJSONObject(i)
            val schema = timeline.getJSONObject("schema")
            val schemaName = schema.getString("name")
            
            if (schemaName.contains("Computer usage", ignoreCase = true)) {
                val timelineKey = timeline.getString("timelineKey")
                val lastChangeId = if (timeline.has("lastChangeId")) timeline.getString("lastChangeId") else null
                val homeEnv = timeline.getJSONObject("homeEnvironment")
                val environmentId = homeEnv.getString("environmentId")
                Log.d(TAG, "使用Computer Usage timeline: $timelineKey")
                return@withContext Triple(timelineKey, lastChangeId, environmentId)
            }
        }
        
        // 如果没有找到Computer Usage，再找Tags
        for (i in 0 until timelines.length()) {
            val timeline = timelines.getJSONObject(i)
            val schema = timeline.getJSONObject("schema")
            val schemaName = schema.getString("name")
            
            if (schemaName == "ManicTime/Tags") {
                val timelineKey = timeline.getString("timelineKey")
                val lastChangeId = if (timeline.has("lastChangeId")) timeline.getString("lastChangeId") else null
                val homeEnv = timeline.getJSONObject("homeEnvironment")
                val environmentId = homeEnv.getString("environmentId")
                Log.d(TAG, "找到Tags timeline: $timelineKey")
                return@withContext Triple(timelineKey, lastChangeId, environmentId)
            }
        }
        
        // 如果都没有找到,使用第一个timeline
        if (timelines.length() > 0) {
            val firstTimeline = timelines.getJSONObject(0)
            val timelineKey = firstTimeline.getString("timelineKey")
            val lastChangeId = if (firstTimeline.has("lastChangeId")) firstTimeline.getString("lastChangeId") else null
            val homeEnv = firstTimeline.getJSONObject("homeEnvironment")
            val environmentId = homeEnv.getString("environmentId")
            val schema = firstTimeline.getJSONObject("schema")
            val schemaName = schema.getString("name")
            Log.d(TAG, "使用第一个timeline: $timelineKey (类型: $schemaName)")
            return@withContext Triple(timelineKey, lastChangeId, environmentId)
        }
        
        throw Exception("未找到可用的Timeline")
    }
    
    /**
     * 批量上传活动记录（使用changes API）
     */
    suspend fun uploadActivities(
        timelineKey: String,
        lastChangeId: String?,
        environmentId: String,
        activities: List<ActivityRecord>
    ) = withContext(Dispatchers.IO) {
        // 确保serverUrl不以/结尾
        val baseUrl = prefs.serverUrl.trimEnd('/')
        val url = "$baseUrl/api/timelines/$timelineKey/changes"
        
        // 构建Schema
        val schema = JSONObject().apply {
            put("Name", "ManicTime/Applications")
            put("Version", "1.0.0.0")
            put("BaseSchema", JSONObject().apply {
                put("Name", "ManicTime/Generic/Group")
                put("Version", "1.0.0.0")
            })
        }
        
        // 构建Changes数组
        val changesArray = JSONArray()
        
        // 先添加groups
        val groupsMap = mutableMapOf<String, Int>()
        var groupEntityId = 1
        val random = java.util.Random()
        activities.forEach { activity ->
            if (!groupsMap.containsKey(activity.packageName)) {
                groupsMap[activity.packageName] = groupEntityId
                changesArray.put(JSONObject().apply {
                    put("ChangeId", "${groupEntityId},${random.nextInt(Int.MAX_VALUE)}")
                    put("ChangeType", "Create")
                    put("EntityId", groupEntityId)
                    put("EntityType", "group")
                    put("OldValues", JSONObject())
                    put("NewValues", JSONObject().apply {
                        put("groupId", activity.packageName)
                        put("displayName", activity.appName)
                        put("color", generateColorForPackage(activity.packageName))
                    })
                })
                groupEntityId++
            }
        }
        
        // 再添加activities
        var activityEntityId = 1000
        activities.forEach { activity ->
            val startTime = dateFormat.format(Date(activity.startTime))
            val duration = activity.duration
            
            changesArray.put(JSONObject().apply {
                put("ChangeId", "${activityEntityId},${random.nextInt(Int.MAX_VALUE)}")
                put("ChangeType", "Create")
                put("EntityId", activityEntityId)
                put("EntityType", "activity")
                put("OldValues", JSONObject.NULL)
                put("NewValues", JSONObject().apply {
                    put("groupId", groupsMap[activity.packageName])
                    put("isActive", false)
                    put("name", activity.appName)
                    put("timeInterval", JSONObject().apply {
                        put("start", startTime)
                        put("duration", duration)
                    })
                })
            })
            activityEntityId++
        }
        
        val json = JSONObject().apply {
            put("Schema", schema)
            put("ExpectedEnvironmentId", environmentId)
            put("ExpectedLastChangeId", lastChangeId ?: JSONObject.NULL)
            put("Changes", changesArray)
        }
        
        Log.d(TAG, "上传 ${activities.size} 条活动记录")
        Log.d(TAG, "请求体: ${json.toString(2)}")
        AppLogger.i(TAG, "上传URL: $url")
        AppLogger.i(TAG, "请求体大小: ${json.toString().length} 字节")
        AppLogger.i(TAG, "Headers: Content-Type=$CONTENT_TYPE_JSON, Accept=$ACCEPT_HEADER")
        AppLogger.i(TAG, "请求体内容:\n${json.toString(2)}")
        
        try {
            post(url, json.toString(), CONTENT_TYPE_JSON)
            AppLogger.i(TAG, "活动上传API调用成功")
        } catch (e: Exception) {
            AppLogger.e(TAG, "活动上传API失败", e)
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
            // 显式设置Host header，避免服务器解析错误
            connection.setRequestProperty("Host", url.host + if (url.port != -1) ":${url.port}" else "")
            // 添加ManicTime环境headers（与Windows客户端一致）
            connection.setRequestProperty("Manictime-Env-Application", "ManicTime Android;1.0.0")
            connection.setRequestProperty("Manictime-Env-Devicename", android.os.Build.MODEL)
            
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