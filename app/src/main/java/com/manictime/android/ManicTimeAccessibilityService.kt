package com.manictime.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * ManicTime无障碍服务
 * 用于收集Documents timeline数据：
 * - 浏览器访问的URL
 * - 应用内的文档标题
 * - 窗口内容变化
 */
class ManicTimeAccessibilityService : AccessibilityService() {
    
    companion object {
        const val TAG = "ManicTimeAccessibility"
        private var instance: ManicTimeAccessibilityService? = null
        
        fun getInstance(): ManicTimeAccessibilityService? = instance
        
        val isRunning: Boolean
            get() = instance != null
    }
    
    private var lastUrl: String? = null
    private var lastTitle: String? = null
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        
        serviceInfo = info
        Log.d(TAG, "无障碍服务已连接")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged(event)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    handleWindowContentChanged(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理事件失败", e)
        }
    }
    
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return
        
        Log.d(TAG, "窗口切换: $packageName - $className")
        
        // 获取窗口标题
        val title = event.text?.firstOrNull()?.toString()
        if (title != null && title != lastTitle) {
            lastTitle = title
            Log.d(TAG, "窗口标题: $title")
            
            // 发送Documents数据
            sendDocumentData(packageName, title, null)
        }
        
        // 检查是否是浏览器
        if (isBrowserPackage(packageName)) {
            extractUrlFromBrowser(event.source)
        }
    }
    
    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // 只处理浏览器的内容变化
        if (isBrowserPackage(packageName)) {
            extractUrlFromBrowser(event.source)
        }
    }
    
    private fun extractUrlFromBrowser(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return
        
        try {
            // 查找URL地址栏
            val urlNode = findUrlBar(nodeInfo)
            if (urlNode != null) {
                val url = urlNode.text?.toString()
                if (url != null && url != lastUrl && url.startsWith("http")) {
                    lastUrl = url
                    Log.d(TAG, "浏览器URL: $url")
                    
                    // 发送Documents数据
                    sendDocumentData(nodeInfo.packageName?.toString() ?: "", 
                                   extractTitle(nodeInfo), 
                                   url)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取URL失败", e)
        } finally {
            nodeInfo.recycle()
        }
    }
    
    private fun findUrlBar(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 常见浏览器的URL栏特征
        val urlBarIds = listOf(
            "url_bar", "location_bar", "search_box", "address_bar",
            "omnibox", "url", "search"
        )
        
        // 递归查找URL栏
        fun searchNode(current: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val viewId = current.viewIdResourceName
            if (viewId != null) {
                for (id in urlBarIds) {
                    if (viewId.contains(id, ignoreCase = true)) {
                        return current
                    }
                }
            }
            
            for (i in 0 until current.childCount) {
                val child = current.getChild(i) ?: continue
                val result = searchNode(child)
                if (result != null) return result
                child.recycle()
            }
            
            return null
        }
        
        return searchNode(node)
    }
    
    private fun extractTitle(node: AccessibilityNodeInfo): String? {
        // 尝试从窗口标题获取
        val windowTitle = node.window?.title?.toString()
        if (!windowTitle.isNullOrEmpty()) {
            return windowTitle
        }
        
        // 尝试从内容描述获取
        return node.contentDescription?.toString()
    }
    
    private fun isBrowserPackage(packageName: String): Boolean {
        val browsers = listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.brave.browser",
            "com.UCMobile.intl",
            "com.qihoo.browser",
            "com.tencent.mtt"
        )
        return browsers.any { packageName.contains(it) }
    }
    
    private fun sendDocumentData(packageName: String, title: String?, url: String?) {
        // 发送到ManicTimeService进行上传
        val service = ManicTimeService.getInstance()
        if (service != null) {
            service.onDocumentChanged(packageName, title, url)
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "服务中断")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "服务销毁")
    }
}
