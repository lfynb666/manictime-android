# 截图存储和上传策略实现总结

## ✅ 已完成的修改

### 1. 配置管理 (ManicTimePreferences.kt)
- ✅ 添加 `uploadOnMobileData` 配置项
- 用途：控制是否在移动数据下上传非截图数据

### 2. 截图管理器 (ScreenshotManager.kt) - 新文件
- ✅ 双版本截图保存：原图 + 缩略图（30%缩放，50%质量）
- ✅ 文件命名格式：`2025-11-20_00-00-06_08-00_3862_2130_347371_1.jpg`
- ✅ 本地永久存储：`/sdcard/Android/data/com.manictime.android/files/ManicTime/Screenshots/`
- ✅ 添加 `.nomedia` 文件，防止被系统图库扫描
- ✅ 待上传标记系统：使用 marker 文件追踪待上传截图
- ✅ 统计功能：获取截图数量、大小等信息

### 3. 网络工具 (NetworkUtils.kt) - 新文件
- ✅ WiFi 检测
- ✅ 网络连接检测
- ✅ 网络类型获取

### 4. 辅助功能服务 (ScreenCaptureAccessibilityService.kt)
- ✅ 集成 ScreenshotManager
- ✅ 截图后自动保存双版本
- ✅ 自动标记为待上传

## 🔄 需要继续的修改

### 5. ManicTimeService.kt - 智能上传逻辑

需要修改 `uploadPendingData()` 方法：

```kotlin
private suspend fun uploadPendingData() = withContext(Dispatchers.IO) {
    if (timelineKey == null) {
        Log.w(TAG, "Timeline未初始化,跳过上传")
        return@withContext
    }
    
    val isWiFi = NetworkUtils.isWiFiConnected(this@ManicTimeService)
    val networkType = NetworkUtils.getNetworkType(this@ManicTimeService)
    
    Log.d(TAG, "当前网络: $networkType")
    
    // 1. 上传活动记录（根据配置决定是否需要WiFi）
    val canUploadActivity = isWiFi || prefs.uploadOnMobileData
    
    if (canUploadActivity && activityQueue.isNotEmpty()) {
        try {
            val activities = activityQueue.toList()
            activityQueue.clear()
            
            for (activity in activities) {
                apiClient.uploadActivity(timelineKey!!, activity)
            }
            
            Log.d(TAG, "上传了 ${activities.size} 条活动记录")
            updateNotification("已同步 ${activities.size} 条活动")
        } catch (e: Exception) {
            Log.e(TAG, "上传活动失败", e)
            activityQueue.addAll(0, activityQueue)
        }
    } else if (!canUploadActivity) {
        Log.d(TAG, "非WiFi环境且未启用移动数据上传，活动数据待发送: ${activityQueue.size}")
    }
    
    // 2. 上传截图（仅WiFi）
    if (isWiFi) {
        val screenshotManager = ScreenshotManager(this@ManicTimeService)
        val pendingScreenshots = screenshotManager.getPendingScreenshots()
        
        if (pendingScreenshots.isNotEmpty()) {
            Log.d(TAG, "开始上传截图，共 ${pendingScreenshots.size} 组")
            
            // 一次最多上传3组
            val toUpload = pendingScreenshots.take(3)
            
            for ((originalFile, thumbnailFile) in toUpload) {
                try {
                    // 上传原图
                    val originalBytes = originalFile.readBytes()
                    apiClient.uploadScreenshot(ScreenshotData(
                        timestamp = originalFile.lastModified(),
                        imageData = originalBytes
                    ))
                    
                    // 上传缩略图
                    val thumbnailBytes = thumbnailFile.readBytes()
                    apiClient.uploadScreenshot(ScreenshotData(
                        timestamp = thumbnailFile.lastModified(),
                        imageData = thumbnailBytes
                    ))
                    
                    // 移除上传标记（但保留本地文件）
                    screenshotManager.removeUploadMarker(originalFile)
                    
                    Log.d(TAG, "截图上传成功: ${originalFile.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "截图上传失败: ${originalFile.name}", e)
                    break // 失败则停止本次上传
                }
            }
            
            updateNotification("已上传 ${toUpload.size} 组截图")
        }
    } else {
        Log.d(TAG, "非WiFi环境，截图待上传")
    }
}
```

### 6. MainActivity.kt - UI更新

需要在设置卡片中添加移动数据上传开关：

```kotlin
// 在 SettingsCard 中添加
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Column(modifier = Modifier.weight(1f)) {
        Text("移动数据上传")
        Text(
            text = "开启后活动数据也在移动网络下上传",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
    Switch(
        checked = uploadOnMobileData,
        onCheckedChange = onUploadOnMobileDataChange
    )
}
```

并在 MainScreen 中添加状态：

```kotlin
var uploadOnMobileData by remember { mutableStateOf(prefs.uploadOnMobileData) }
```

### 7. 添加截图查看功能（可选）

在 MainActivity 中添加一个卡片显示截图统计：

```kotlin
@Composable
fun ScreenshotStatsCard() {
    val screenshotManager = remember { ScreenshotManager(LocalContext.current) }
    var stats by remember { mutableStateOf<ScreenshotStatistics?>(null) }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            stats = screenshotManager.getStatistics()
        }
    }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("📸 截图统计", style = MaterialTheme.typography.titleMedium)
            
            stats?.let {
                Text("总数: ${it.totalCount} 张")
                Text("原图: ${it.originalSize / 1024 / 1024} MB")
                Text("缩略图: ${it.thumbnailSize / 1024 / 1024} MB")
                Text("总大小: ${it.totalSize / 1024 / 1024} MB")
                Text(
                    "存储位置: ${it.storageDir}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
```

## 📋 工作原理

### 截图流程
1. 辅助功能服务定时截图
2. 使用 `screencap` 命令获取原始截图
3. ScreenshotManager 保存两个版本：
   - 原图：90% 质量 JPEG
   - 缩略图：30% 缩放 + 50% 质量
4. 创建 marker 文件标记为待上传
5. 本地永久保存，不删除

### 上传流程
1. ManicTimeService 定时检查网络状态
2. **活动数据**：
   - WiFi：立即上传
   - 移动数据：根据 `uploadOnMobileData` 配置决定
3. **截图数据**：
   - 仅 WiFi：上传原图 + 缩略图
   - 移动数据：不上传，等待 WiFi
4. 上传成功后删除 marker，但保留本地文件

### 隐私保护
- 截图保存在应用私有目录
- 添加 `.nomedia` 文件防止被图库扫描
- 用户可通过文件管理器手动访问

## 🎯 优势

1. **节省流量**：缩略图只有原图的 10-20%
2. **本地备份**：所有截图永久保存
3. **智能上传**：根据网络类型自动调整
4. **用户可控**：可配置移动数据上传策略
5. **隐私保护**：不被系统图库扫描

## 📱 用户体验

- WiFi 环境：全自动，无感上传
- 移动数据环境：
  - 活动数据：可配置是否上传
  - 截图：等待 WiFi，本地保存
- 本地查看：通过文件管理器访问截图目录

## 🔧 待完成

1. 修改 ManicTimeService 的 uploadPendingData 方法
2. 在 MainActivity 添加移动数据上传开关
3. 添加截图统计显示（可选）
4. 测试网络切换场景
5. 测试大量截图上传性能

要我继续完成这些修改吗？
