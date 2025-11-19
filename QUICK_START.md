# 🚀 快速开始 - 5分钟上手GitHub自动构建

## 📋 前置要求

- ✅ Git已安装（[下载](https://git-scm.com/download/win)）
- ✅ GitHub账号（[注册](https://github.com/signup)）
- ✅ 项目文件已准备好

---

## ⚡ 三步走

### 第一步：重组项目结构（2分钟）

在项目目录打开PowerShell：

```powershell
cd c:\Users\37666\manictimeapp

# 运行自动化脚本
.\reorganize.ps1
```

**脚本会自动：**
- ✅ 创建标准Android项目结构
- ✅ 移动所有源文件到正确位置
- ✅ 创建必需的配置文件
- ✅ 下载Gradle Wrapper

---

### 第二步：推送到GitHub（2分钟）

#### 2.1 初始化Git仓库

```bash
# 初始化
git init

# 添加所有文件
git add .

# 提交
git commit -m "Initial commit: ManicTime Android Client"
```

#### 2.2 创建GitHub仓库

1. 访问：https://github.com/new
2. 填写：
   - Repository name: `manictime-android`
   - Visibility: **Private**（推荐，保护隐私）
3. 点击"Create repository"

#### 2.3 推送代码

```bash
# 添加远程仓库（替换YOUR_USERNAME）
git remote add origin https://github.com/YOUR_USERNAME/manictime-android.git

# 推送
git branch -M main
git push -u origin main
```

**首次推送需要认证：**
- 用户名：你的GitHub用户名
- 密码：Personal Access Token（不是GitHub密码）
  - 获取：GitHub → Settings → Developer settings → Personal access tokens → Generate new token
  - 权限：勾选`repo`

---

### 第三步：等待构建完成（5-10分钟）

#### 3.1 查看构建进度

1. 访问你的仓库：`https://github.com/YOUR_USERNAME/manictime-android`
2. 点击顶部"Actions"标签
3. 看到工作流正在运行（黄色圆圈）

#### 3.2 下载APK

构建完成后（绿色勾号）：
1. 点击完成的工作流
2. 向下滚动到"Artifacts"
3. 下载：
   - `manictime-debug.zip` - 测试版
   - `manictime-release.zip` - 正式版
4. 解压得到APK文件

---

## 📱 安装到手机

### 方法1：USB连接

```bash
# 确保手机已开启USB调试
adb install manictime-debug.apk
```

### 方法2：直接传输

1. 将APK传到手机（微信/QQ/云盘）
2. 在手机上点击APK安装
3. 允许"未知来源"安装

---

## 🔄 日常使用

### 修改代码后重新构建

```bash
# 1. 修改代码（在app/src/main/java/com/manictime/android/目录下）

# 2. 提交并推送
git add .
git commit -m "描述你的修改"
git push

# 3. GitHub自动开始构建

# 4. 5-10分钟后在Actions页面下载新APK
```

---

## 🐛 常见问题

### Q1: 脚本执行失败 - "无法加载文件"

**错误信息：**
```
无法加载文件 reorganize.ps1，因为在此系统上禁止运行脚本
```

**解决方案：**
```powershell
# 临时允许脚本执行
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

# 然后重新运行脚本
.\reorganize.ps1
```

### Q2: Git推送失败 - 认证错误

**错误信息：**
```
remote: Support for password authentication was removed
```

**解决方案：**
使用Personal Access Token代替密码：
1. GitHub → Settings → Developer settings → Personal access tokens
2. Generate new token (classic)
3. 勾选`repo`权限
4. 复制token（只显示一次！）
5. 推送时用token作为密码

### Q3: GitHub Actions构建失败

**可能原因：**
- Gradle Wrapper文件缺失
- 项目结构不正确
- 网络问题

**解决方案：**
1. 查看Actions日志找到具体错误
2. 参考`GITHUB_GUIDE.md`的故障排除章节
3. 确保所有文件都在正确位置

### Q4: 下载的APK无法安装

**可能原因：**
- 签名问题
- Android版本不兼容

**解决方案：**
1. 使用debug版本测试
2. 确保手机是Android 8.0+
3. 检查是否允许"未知来源"安装

---

## 📚 详细文档

- **完整GitHub指南**：`GITHUB_GUIDE.md`
- **项目结构说明**：`PROJECT_STRUCTURE.md`
- **功能使用手册**：`Readme.md`

---

## ✅ 检查清单

在推送到GitHub前，确认：

- [ ] 运行了`reorganize.ps1`脚本
- [ ] 所有`.kt`文件在`app/src/main/java/com/manictime/android/`
- [ ] `AndroidManifest.xml`在`app/src/main/`
- [ ] 存在`gradle/wrapper/gradle-wrapper.jar`
- [ ] 存在`gradlew`和`gradlew.bat`
- [ ] 存在`.github/workflows/build-apk.yml`

验证命令：
```powershell
# 检查关键文件
dir app\src\main\java\com\manictime\android\*.kt
dir app\src\main\AndroidManifest.xml
dir gradle\wrapper\gradle-wrapper.jar
dir gradlew*
dir .github\workflows\build-apk.yml
```

---

## 🎉 完成！

现在你有了：
- ✅ 标准的Android项目结构
- ✅ 自动化的GitHub Actions构建
- ✅ 无需本地Android Studio
- ✅ 每次推送自动生成APK

**享受自动化构建的便利！** 🚀

有问题随时查看详细文档或提issue。
