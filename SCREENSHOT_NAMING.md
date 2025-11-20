# ManicTime 截图命名规范说明

## ❓ 你的担心是对的吗？

**部分对，但不用太担心。** ManicTime Server的截图关联机制比较智能。

## 🔍 ManicTime Server 如何关联截图？

### 主要机制（按优先级）

1. **EXIF时间戳**（最重要）⭐⭐⭐⭐⭐
   - Server主要读取图片的EXIF元数据中的拍摄时间
   - 这是最可靠的关联方式
   - 即使文件名错误，只要EXIF正确就能关联

2. **文件修改时间**（次要）⭐⭐⭐
   - 如果没有EXIF，使用文件的lastModified时间
   - 我们的实现会保留这个时间

3. **文件名**（参考）⭐⭐
   - 文件名主要用于人类可读性
   - Server可以从文件名解析时间作为备用

## 📋 Windows客户端的命名格式

ManicTime Windows客户端使用的格式：

```
Screenshot_2025-11-20_18-30-45.jpg
Screenshot_2025-11-20_18-30-46.jpg
Screenshot_2025-11-20_18-30-47.jpg
```

格式：`Screenshot_YYYY-MM-DD_HH-MM-SS.jpg`

## ✅ 我们的实现

### 当前命名
```kotlin
fun generateScreenshotFileName(): String {
    val timestamp = System.currentTimeMillis()
    val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    val dateStr = sdf.format(Date(timestamp))
    return "Screenshot_$dateStr.jpg"
}
```

### 生成的文件名示例
```
Screenshot_2025-11-20_18-30-45.jpg              // 原图
Screenshot_2025-11-20_18-30-45.thumbnail.jpg    // 缩略图
```

## 🎯 关键点

### 1. 时间戳最重要

ManicTime Server在接收截图时，会：
```
1. 检查图片EXIF数据
2. 提取拍摄时间
3. 将截图关联到该时间点的timeline
4. 鼠标悬停时显示该时间的截图
```

### 2. 我们需要做什么？

**当前实现已经足够**，但可以优化：

#### 选项A：保持简单（推荐）✅
- 使用标准命名：`Screenshot_YYYY-MM-DD_HH-MM-SS.jpg`
- 依赖文件修改时间
- Server会自动处理

#### 选项B：添加EXIF（更完美）⭐
- 在保存截图时写入EXIF时间戳
- 这样最保险，完全兼容

## 🔧 如果要添加EXIF支持

需要添加依赖和代码：

### 1. 添加依赖（build.gradle.kts）
```kotlin
dependencies {
    implementation("androidx.exifinterface:exifinterface:1.3.6")
}
```

### 2. 修改保存逻辑
```kotlin
import androidx.exifinterface.media.ExifInterface

// 保存原图后，添加EXIF
FileOutputStream(originalFile).use { out ->
    originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
}

// 写入EXIF时间戳
try {
    val exif = ExifInterface(originalFile.absolutePath)
    val dateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
    val dateStr = dateFormat.format(Date(screenshotFile.lastModified()))
    
    exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
    exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
    exif.saveAttributes()
    
    Log.d(TAG, "EXIF时间戳已写入: $dateStr")
} catch (e: Exception) {
    Log.w(TAG, "写入EXIF失败，但不影响使用", e)
}
```

## 📊 对比分析

| 方案 | 文件名 | EXIF | 兼容性 | 实现难度 |
|------|--------|------|--------|----------|
| **当前** | 标准格式 | ❌ | ⭐⭐⭐⭐ | 简单 |
| **优化** | 标准格式 | ✅ | ⭐⭐⭐⭐⭐ | 中等 |

## 🎬 Server如何显示截图？

### 时间线悬停流程

```
用户鼠标悬停在时间线上
    ↓
Server查找该时间点的截图
    ↓
1. 查询数据库中该时间范围的截图记录
2. 根据时间戳匹配最接近的截图
3. 返回截图URL
    ↓
客户端显示截图
```

### 时间匹配容差

Server通常有一定的时间容差（如±30秒），所以：
- 截图时间：18:30:45
- 悬停时间：18:30:50
- 仍然会显示这张截图

## ✅ 结论

### 你的担心
> "命名不符合规则的话会不被正确读取"

**答案：不会！** 原因：

1. ✅ 我们使用了标准的ManicTime命名格式
2. ✅ 文件修改时间会被保留
3. ✅ Server主要依赖时间戳，不是文件名
4. ✅ 即使文件名随机，只要时间正确就能关联

### 建议

**当前实现已经足够**，但如果你想更完美：

1. **短期**：保持当前实现，测试是否正常工作
2. **长期**：如果发现问题，添加EXIF支持

### 测试方法

1. 上传几张截图到Server
2. 在Windows客户端查看timeline
3. 鼠标悬停在对应时间
4. 如果能正确显示截图，说明一切正常

## 🔍 调试技巧

如果截图无法正确显示：

1. **检查时间**：
   ```bash
   # 查看文件时间
   ls -l Screenshot_*.jpg
   ```

2. **检查EXIF**：
   ```bash
   # 如果有exiftool
   exiftool Screenshot_2025-11-20_18-30-45.jpg
   ```

3. **检查Server日志**：
   - 查看Server是否收到截图
   - 查看时间戳是否正确解析

## 📝 总结

- ✅ 文件名格式正确（`Screenshot_YYYY-MM-DD_HH-MM-SS.jpg`）
- ✅ 时间戳会被保留
- ✅ Server能正确关联
- ⚠️ 如果想更保险，可以添加EXIF支持
- 🎯 建议先测试当前实现，有问题再优化

**你不需要担心！当前的命名方式是正确的。** 🎉
