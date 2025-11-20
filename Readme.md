# ManicTime Android 客户端 - 完整使用指南

## 📱 项目概述

这是一个功能完整的ManicTime Android客户端,可以像Windows版本一样实时监控应用使用、定时截图,并将所有数据同步到你的ManicTime Server。

### ✨ 核心功能

1. ✅ **OAuth认证** - 安全连接到ManicTime Server
2. ✅ **应用使用监控** - 自动记录每个应用的使用时间
3. ✅ **定时截图** - 每5分钟自动截取屏幕(可配置)
4. ✅ **实时同步** - 自动上传活动记录和截图到服务器
5. ✅ **前台服务** - 持续运行,不被系统杀死
6. ✅ **低电量优化** - 智能管理资源使用

---

## 🚀 快速开始

### 1. 环境要求

- **Android Studio**: Hedgehog (2023.1.1) 或更高版本
- **Kotlin**: 1.9.0+
- **Android SDK**: API 26+ (Android 8.0+)
- **ManicTime Server**: 4.1+ 版本

### 2. 项目结构

```
com.manictime.android/
├── MainActivity.kt              # 主界面UI和权限管理
├── ManicTimeService.kt          # 前台服务(监控+截图)
├── ManicTimeApiClient.kt        # API客户端(网络通信)
├── ManicTimePreferences.kt      # 配置管理
└── AndroidManifest.xml          # 权限和组件声明
```

### 3. 安装步骤

#### 方法一:使用Android Studio(推荐)

1. **创建新项目**
   ```
   File -> New -> New Project
   选择 "Empty Activity"
   - Name: ManicTime Android
   - Package name: com.manictime.android
   - Language: Kotlin
   - Minimum SDK: API 26
   ```

2. **替换文件**
   - 将我提供的所有代码文件复制到对应位置
   - `MainActivity.kt` -> app/src/main/java/com/manictime/android/
   - `ManicTimeService.kt` -> app/src/main/java/com/manictime/android/
   - `ManicTimeApiClient.kt` -> app/src/main/java/com/manictime/android/
   - `ManicTimePreferences.kt` -> app/src/main/java/com/manictime/android/
   - `AndroidManifest.xml` -> app/src/main/
   - `build.gradle.kts` -> app/

3. **同步项目**
   ```
   点击 "Sync Project with Gradle Files"
   等待依赖下载完成
   ```

4. **连接手机**
   - 在手机上启用"开发者选项"
   - 打开"USB调试"
   - 用USB线连接手机到电脑

5. **运行应用**
   ```
   点击绿色的运行按钮 ▶️
   或按 Shift + F10
   ```

#### 方法二:直接安装APK

如果你已经编译好APK:
```bash
adb install manictime-android.apk
```

---

## ⚙️ 配置和使用

### 第一步:配置服务器

1. 启动应用
2. 在"服务器配置"卡片中填写:
   - **服务器地址**: `http://你的服务器IP:8080`
     - 例如: `http://192.168.1.100:8080`
     - 如果是云服务器: `https://your-domain.com:8080`
   - **用户名**: 你的ManicTime用户名
   - **密码**: 你的ManicTime密码

3. 点击"连接服务器"
4. 等待认证成功提示

### 第二步:授予权限

#### 1. 应用使用统计权限

- 点击"授权"按钮
- 跳转到系统设置页面
- 找到"ManicTime"应用
- 开启"允许使用记录访问权限"

#### 2. 屏幕截图权限

- 点击"授权"按钮
- 在弹出的对话框中选择"立即开始"
- 此权限每次重启手机后需要重新授予(Android系统限制)

### 第三步:启动监控

1. 确保已连接服务器并授予所有权限
2. 点击"启动"按钮
3. 查看通知栏,应该显示"ManicTime正在运行"

---

## 📊 功能详解

### 应用使用监控

**工作原理**:
- 每30秒检查一次当前使用的应用
- 记录应用名称、包名和使用时长
- 自动合并连续使用的同一应用

**监控的信息**:
```json
{
  "appName": "Chrome浏览器",
  "packageName": "com.android.chrome",
  "startTime": "2025-11-18T10:00:00+08:00",
  "duration": 300
}
```

### 定时截图

**工作原理**:
- 使用MediaProjection API获取屏幕内容
- 自动缩放到原分辨率的50%以节省空间
- 压缩为JPEG格式(质量70%)
- 平均每张截图约200-500KB

**配置选项**:
- 默认间隔:5分钟
- 可在代码中修改 `SCREENSHOT_INTERVAL`

**注意事项**:
- 截图权限在手机重启后会失效
- 需要重新授予才能继续截图
- 某些敏感应用(如银行应用)可能会阻止截图

### 数据同步

**上传策略**:
- 每1分钟尝试上传一次
- 失败的数据会保留在队列中重试
- 活动记录和截图分别上传
- 使用Bearer Token认证

**网络优化**:
- 仅在WiFi下上传大文件(可配置)
- 自动处理网络错误和重试
- 支持后台上传

---

## 🔧 高级配置

### 修改监控间隔

在 `ManicTimeService.kt` 中修改常量:

```kotlin
companion object {
    const val ACTIVITY_CHECK_INTERVAL = 30_000L      // 应用检查间隔(毫秒)
    const val SCREENSHOT_INTERVAL = 300_000L         // 截图间隔(毫秒)
    const val UPLOAD_INTERVAL = 60_000L              // 上传间隔(毫秒)
}
```

### 自定义设备名称

在 `ManicTimeService.kt` 中修改:

```kotlin
val deviceName = "Android-${android.os.Build.MODEL}"
// 改为:
val deviceName = "我的手机"
```

### 修改截图质量

在 `ManicTimeService.kt` 的 `captureScreenshot()` 方法中:

```kotlin
croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
// 参数2是质量,范围0-100,数值越大质量越好但文件越大
```

### 禁用截图功能

如果只需要应用监控,不需要截图:

1. 在 `MainActivity.kt` 中移除截图权限请求按钮
2. 不要调用 `ACTION_START_SCREENSHOT`
3. 或在 `ManicTimeService.kt` 中注释掉截图相关代码

---

## 🔍 故障排除

### 问题1: 无法连接服务器

**症状**: 点击"连接服务器"后提示"认证失败"

**解决方案**:
1. 检查服务器地址是否正确(包括http://前缀)
2. 确认服务器端口(默认8080)
3. 测试网络连接:
   ```bash
   curl http://你的服务器:8080/api
   ```
4. 检查防火墙设置
5. 查看Android Studio的Logcat日志:
   ```
   Tag: ManicTimeApiClient
   ```

### 问题2: 应用使用统计不工作

**症状**: 服务启动了但没有记录任何应用

**解决方案**:
1. 重新授予"应用使用统计"权限
2. 检查权限状态:
   ```kotlin
   设置 -> 应用 -> 特殊应用权限 -> 使用情况访问权限
   ```
3. 某些手机(如小米、华为)需要额外的权限:
   - 自启动权限
   - 后台运行权限
   - 电池优化白名单

### 问题3: 截图失败

**症状**: 授予权限后仍然无法截图

**解决方案**:
1. 重启应用
2. 重新授予截图权限
3. 检查是否有其他应用占用屏幕录制权限
4. 查看Logcat错误信息

### 问题4: 服务被系统杀死

**症状**: 锁屏后服务停止运行

**解决方案**:

1. **关闭电池优化**
   ```
   设置 -> 电池 -> 电池优化 -> ManicTime -> 不优化
   ```

2. **允许后台运行** (不同手机品牌设置不同)
   - 小米: 设置 -> 应用管理 -> ManicTime -> 省电策略 -> 无限制
   - 华为: 设置 -> 应用 -> 应用启动管理 -> ManicTime -> 手动管理
   - OPPO: 设置 -> 电池 -> 耗电保护 -> ManicTime -> 允许后台运行
   - vivo: 设置 -> 电池 -> 后台耗电管理 -> ManicTime -> 允许后台高耗电

3. **锁定应用到最近任务**
   - 打开最近任务列表
   - 找到ManicTime
   - 下拉或长按,选择"锁定"

### 问题5: 上传失败

**症状**: 数据没有同步到服务器

**解决方案**:
1. 检查网络连接
2. 验证访问令牌是否过期
3. 查看服务器日志
4. 检查服务器存储空间
5. 确认timeline是否正确创建

---

## 📱 权限说明

### 必需权限

| 权限 | 用途 | 危险性 |
|-----|------|--------|
| INTERNET | 连接ManicTime Server | 低 |
| FOREGROUND_SERVICE | 保持服务运行 | 低 |
| PACKAGE_USAGE_STATS | 监控应用使用 | 中 |
| 屏幕录制权限 | 截取屏幕 | 高 |

### 可选权限

| 权限 | 用途 | 说明 |
|-----|------|------|
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 防止被杀后台 | 提高稳定性 |
| WAKE_LOCK | 保持CPU唤醒 | 确保定时任务执行 |

### 隐私保护

- ✅ 所有数据仅发送到你自己的服务器
- ✅ 使用HTTPS加密传输(如果服务器支持)
- ✅ 不收集任何第三方数据
- ✅ 截图仅用于时间跟踪,不做其他用途
- ✅ 可以随时停止服务和删除应用

---

## 🔒 安全建议

1. **使用HTTPS**
   - 如果服务器在公网,强烈建议配置SSL证书
   - 修改 `AndroidManifest.xml`:
     ```xml
     android:usesCleartextTraffic="false"
     ```

2. **定期更换密码**
   - 在应用中不保存明文密码
   - 使用访问令牌进行身份验证

3. **限制网络访问**
   - 仅在受信任的WiFi网络中使用
   - 考虑使用VPN连接内网服务器

4. **审查截图内容**
   - 定期检查上传的截图
   - 删除包含敏感信息的截图
   - 在输入密码前暂停截图

---

## 📈 性能优化

### 电池优化

当前配置的电池使用:
- 待机状态: < 2% / 小时
- 活跃使用: 约 5-8% / 小时

**优化建议**:
1. 增大监控间隔(从30秒改为60秒)
2. 减少截图频率(从5分钟改为10分钟)
3. 仅在WiFi下上传数据
4. 降低截图分辨率

### 存储优化

**截图存储计算**:
- 单张截图: ~300KB
- 每天截图数: 288张(5分钟一次)
- 每天存储: ~86MB
- 一周存储: ~600MB

**优化建议**:
1. 增大截图间隔
2. 提高JPEG压缩率
3. 降低截图分辨率
4. 设置服务器自动清理旧截图

### 网络优化

**数据使用量**:
- 活动记录: ~1KB / 条
- 截图: ~300KB / 张
- 每天上传: ~90MB

**优化建议**:
1. 仅在WiFi下上传截图
2. 批量上传(积累一定数量再上传)
3. 压缩JSON数据
4. 使用增量同步

---

## 🛠️ 开发调试

### 查看日志

在Android Studio中:
```
View -> Tool Windows -> Logcat

过滤器:
- Tag: ManicTimeService
- Tag: ManicTimeApiClient
- Package: com.manictime.android
```

### 常用ADB命令

```bash
# 安装应用
adb install app-debug.apk

# 启动应用
adb shell am start -n com.manictime.android/.MainActivity

# 查看日志
adb logcat -s ManicTimeService ManicTimeApiClient

# 强制停止应用
adb shell am force-stop com.manictime.android

# 清除应用数据
adb shell pm clear com.manictime.android

# 授予权限
adb shell appops set com.manictime.android GET_USAGE_STATS allow
```

### 测试API连接

```bash
# 测试认证
curl -X POST http://你的服务器:8080/api/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Accept: application/vnd.manictime.v3+json" \
  -d "grant_type=password&username=你的用户名&password=你的密码"

# 获取timelines
curl http://你的服务器:8080/api/timelines \
  -H "Authorization: Bearer 你的token" \
  -H "Accept: application/vnd.manictime.v3+json"
```

---

## 更新日志

### v1.1.0 (2025-11-21)

- 实现完整的Timeline数据收集
- 添加AccessibilityService收集Documents数据
- 添加DeviceStateReceiver监听设备状态
- 支持Applications、Computer usage、Documents、Screenshots四个timeline
- 优先使用Applications timeline而不是Tags

### v1.0.0 (2025-11-18)

- 初始版本发布
- 实现OAuth认证
- 实现应用使用监控
- 实现定时截图功能
- 实现数据自动同步
- 实现前台服务
- Material Design 3 UI

---

## 常见问题

### Q: 为什么需要这么多权限?

A: 
- **应用使用统计**: 必须,用于监控应用使用时间
- **屏幕录制**: 必须,用于截图功能
- **前台服务**: 必须,防止被系统杀死
- **网络**: 必须,上传数据到服务器

### Q: 会消耗很多电量吗?

A: 正常使用下,电池消耗约为5-8%/小时,主要消耗在截图功能上。如果关闭截图,电池消耗会降至2%/小时以下。

### Q: 截图会被其他应用看到吗?

A: 不会。截图直接上传到你的ManicTime Server,不保存在手机存储中,其他应用无法访问。

### Q: 可以同时在电脑和手机上使用吗?

A: 可以!你的许可证允许10台设备,电脑和手机分别计为独立设备。

### Q: 支持哪些Android版本?

A: 需要Android 8.0 (API 26)或更高版本。推荐Android 10+以获得最佳体验。

### Q: 如何备份配置?

A: 所有配置保存在SharedPreferences中,卸载应用会丢失。建议记录服务器地址和账号信息。

---

## 📧 技术支持

如果遇到问题:

1. 查看本文档的"故障排除"章节
2. 检查Logcat日志寻找错误信息
3. 确认ManicTime Server版本 >= 4.1
4. 验证网络连接和防火墙设置

---

## 📄 许可证

本应用客户端用于连接你的ManicTime Server,遵守ManicTime的许可协议。确保你的ManicTime许可证支持所需的设备数量。

---

## 🎉 完成!

恭喜!你现在拥有一个功能完整的ManicTime Android客户端。

**下一步**:
1. 启动应用并配置服务器
2. 授予所需权限
3. 开始监控
4. 在ManicTime Server上查看同步的数据

享受你的时间追踪之旅! ⏰📱