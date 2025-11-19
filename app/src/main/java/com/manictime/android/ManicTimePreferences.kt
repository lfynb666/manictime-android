package com.manictime.android

import android.content.Context
import android.content.SharedPreferences

/**
 * ManicTime配置管理
 */
class ManicTimePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "manictime_prefs",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SCREENSHOT_ENABLED = "screenshot_enabled"
        private const val KEY_SCREENSHOT_INTERVAL = "screenshot_interval"
        private const val KEY_ACTIVITY_INTERVAL = "activity_interval"
    }
    
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()
    
    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()
    
    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()
    
    var accessToken: String
        get() = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()
    
    var deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id == null) {
                id = java.util.UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()
    
    var screenshotEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREENSHOT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SCREENSHOT_ENABLED, value).apply()
    
    var screenshotInterval: Long
        get() = prefs.getLong(KEY_SCREENSHOT_INTERVAL, 300_000L) // 默认5分钟
        set(value) = prefs.edit().putLong(KEY_SCREENSHOT_INTERVAL, value).apply()
    
    var activityInterval: Long
        get() = prefs.getLong(KEY_ACTIVITY_INTERVAL, 30_000L) // 默认30秒
        set(value) = prefs.edit().putLong(KEY_ACTIVITY_INTERVAL, value).apply()
    
    fun clearAuth() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .apply()
    }
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    fun isConfigured(): Boolean {
        return serverUrl.isNotEmpty() && 
               username.isNotEmpty() && 
               password.isNotEmpty()
    }
    
    fun isAuthenticated(): Boolean {
        return accessToken.isNotEmpty()
    }
}