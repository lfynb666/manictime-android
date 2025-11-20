package com.manictime.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 开机自启动广播接收器
 * 在设备启动后自动启动ManicTime服务
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到广播: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                // 检查服务是否应该自动启动
                val prefs = ManicTimePreferences(context)
                
                if (prefs.isConfigured() && prefs.autoStartEnabled) {
                    Log.d(TAG, "开机自启动ManicTime服务")
                    
                    val serviceIntent = Intent(context, ManicTimeService::class.java).apply {
                        action = ManicTimeService.ACTION_START
                    }
                    
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        Log.d(TAG, "服务启动成功")
                    } catch (e: Exception) {
                        Log.e(TAG, "服务启动失败", e)
                    }
                } else {
                    Log.d(TAG, "自动启动未启用或未配置")
                }
            }
        }
    }
}
