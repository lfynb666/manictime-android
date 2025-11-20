package com.manictime.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * 设备状态接收器
 * 监听屏幕开关、锁屏等状态，用于Computer usage timeline
 */
class DeviceStateReceiver : BroadcastReceiver() {
    
    companion object {
        const val TAG = "DeviceStateReceiver"
        
        fun register(context: Context): DeviceStateReceiver {
            val receiver = DeviceStateReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)  // 解锁
            }
            context.registerReceiver(receiver, filter)
            return receiver
        }
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        val state = when (intent.action) {
            Intent.ACTION_SCREEN_ON -> DeviceState.ACTIVE
            Intent.ACTION_SCREEN_OFF -> DeviceState.IDLE
            Intent.ACTION_USER_PRESENT -> DeviceState.ACTIVE
            else -> return
        }
        
        Log.d(TAG, "设备状态变化: $state")
        
        // 发送到服务
        val service = ManicTimeService.getInstance()
        service?.onDeviceStateChanged(state)
    }
}

enum class DeviceState {
    ACTIVE,   // 活动（屏幕开启）
    IDLE,     // 空闲（屏幕关闭）
    LOCKED    // 锁定
}
