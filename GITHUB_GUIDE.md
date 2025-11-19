# 📦 GitHub自动构建完整指南

## 🎯 目标
通过GitHub Actions自动构建APK，无需本地配置Android开发环境。

---

## 📁 第一步：整理项目结构

### 当前结构（需要调整）
```
manictimeapp/
├── AndroidManifest.xml
├── MainActivity.kt
├── ManicTimeApiClient.kt
├── ManicTimePreferences.kt
├── ManicTimeServer.kt
├── build.gradle.kts
└── ...
```

### 标准Android项目结构（目标）
```
manictimeapp/
├── .github/
│   └── workflows/
│       └── build-apk.yml          # GitHub Actions配置
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/manictime/android/
│   │       │   ├── MainActivity.kt
│   │       │   ├── ManicTimeService.kt
│   │       │   ├── ManicTimeApiClient.kt
│   │       │   └── ManicTimePreferences.kt
│   │       ├── AndroidManifest.xml
│   │       └── res/              # 资源文件（图标等）
│   └── build.gradle.kts          # app模块配置
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build.gradle.kts              # 项目级配置
├── settings.gradle.kts
├── gradle.properties
├── gradlew                       # Linux/Mac构建脚本
├── gradlew.bat                   # Windows构建脚本
├── .gitignore
└── README.md
```

---

## 🔧 第二步：创建标准项目结构

### 方案A：使用命令行（推荐）

在`c:\Users\37666\manictimeapp`目录下执行：

```powershell
# 1. 创建app目录结构
mkdir -p app\src\main\java\com\manictime\android
mkdir -p app\src\main\res\mipmap-hdpi
mkdir -p app\src\main\res\mipmap-mdpi
mkdir -p app\src\main\res\mipmap-xhdpi
mkdir -p app\src\main\res\mipmap-xxhdpi
mkdir -p app\src\main\res\mipmap-xxxhdpi
mkdir -p app\src\main\res\values

# 2. 移动Kotlin文件
move MainActivity.kt app\src\main\java\com\manictime\android\
move ManicTimeServer.kt app\src\main\java\com\manictime\android\
move ManicTimeApiClient.kt app\src\main\java\com\manictime\android\
move ManicTimePreferences.kt app\src\main\java\com\manictime\android\

# 3. 移动AndroidManifest.xml
move AndroidManifest.xml app\src\main\

# 4. 移动build.gradle.kts到app目录
move build.gradle.kts app\

# 5. 下载Gradle Wrapper（重要！）
# 访问 https://services.gradle.org/distributions/gradle-8.4-bin.zip
# 解压后将gradle文件夹、gradlew、gradlew.bat复制到项目根目录
```

### 方案B：手动操作（如果命令行不熟悉）

1. 在Windows资源管理器中打开`c:\Users\37666\manictimeapp`
2. 创建文件夹：`app\src\main\java\com\manictime\android`
3. 将所有`.kt`文件拖到`app\src\main\java\com\manictime\android\`
4. 将`AndroidManifest.xml`拖到`app\src\main\`
5. 将`build.gradle.kts`拖到`app\`目录

---

## 📝 第三步：创建必需的配置文件

### 1. 项目根目录的`build.gradle.kts`

创建文件：`c:\Users\37666\manictimeapp\build.gradle.kts`

```kotlin
// Top-level build file
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "1.9.20" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
```

### 2. Gradle Wrapper配置

创建文件：`c:\Users\37666\manictimeapp\gradle\wrapper\gradle-wrapper.properties`

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

### 3. 资源文件

创建文件：`c:\Users\37666\manictimeapp\app\src\main\res\values\strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">ManicTime</string>
</resources>
```

### 4. 创建临时图标（可选）

如果没有图标，GitHub Actions会报错。可以暂时使用Android默认图标：
- 从任何Android项目复制`res/mipmap-*`文件夹
- 或者在构建时会自动使用默认图标

---

## 🚀 第四步：推送到GitHub

### 1. 安装Git（如果还没有）

下载：https://git-scm.com/download/win

### 2. 初始化Git仓库

在项目目录打开PowerShell或Git Bash：

```bash
# 初始化仓库
git init

# 添加所有文件
git add .

# 提交
git commit -m "Initial commit: ManicTime Android Client"
```

### 3. 创建GitHub仓库

1. 访问 https://github.com/new
2. 填写信息：
   - **Repository name**: `manictime-android`
   - **Description**: `ManicTime Android客户端 - 自动监控应用使用和截图`
   - **Visibility**: 选择`Private`（私有仓库，保护隐私）
   - 不要勾选"Initialize this repository with a README"
3. 点击"Create repository"

### 4. 推送代码

复制GitHub显示的命令，在项目目录执行：

```bash
# 添加远程仓库（替换YOUR_USERNAME为你的GitHub用户名）
git remote add origin https://github.com/YOUR_USERNAME/manictime-android.git

# 推送代码
git branch -M main
git push -u origin main
```

**首次推送需要GitHub认证：**
- 用户名：你的GitHub用户名
- 密码：使用Personal Access Token（不是GitHub密码）
  - 获取Token：GitHub -> Settings -> Developer settings -> Personal access tokens -> Tokens (classic) -> Generate new token
  - 权限选择：`repo`（完整仓库访问权限）

---

## ⚙️ 第五步：配置GitHub Actions

### 1. 检查工作流文件

确认文件存在：`.github/workflows/build-apk.yml`

如果不存在，创建它（内容已在前面生成）。

### 2. 启用GitHub Actions

1. 访问你的仓库：`https://github.com/YOUR_USERNAME/manictime-android`
2. 点击顶部的"Actions"标签
3. 如果提示启用Actions，点击"I understand my workflows, go ahead and enable them"

### 3. 触发构建

**方式1：推送代码触发**
```bash
# 修改任意文件后
git add .
git commit -m "Trigger build"
git push
```

**方式2：手动触发**
1. 访问仓库的"Actions"页面
2. 左侧选择"Build Android APK"
3. 点击右侧"Run workflow"按钮
4. 选择分支（main）
5. 点击绿色"Run workflow"按钮

### 4. 查看构建进度

1. 在"Actions"页面会看到新的工作流运行
2. 点击进入查看详细日志
3. 等待约5-10分钟（首次构建较慢）

---

## 📥 第六步：下载APK

### 构建成功后

1. 在Actions页面，点击完成的工作流
2. 向下滚动到"Artifacts"部分
3. 会看到两个文件：
   - `manictime-debug` - 调试版本（用于测试）
   - `manictime-release` - 发布版本（正式使用）
4. 点击下载（会下载为zip文件）
5. 解压得到APK文件

### 安装到手机

**方法1：USB连接**
```bash
adb install manictime-debug.apk
```

**方法2：直接传输**
1. 将APK文件传到手机（微信、QQ、云盘等）
2. 在手机上打开文件管理器
3. 点击APK文件安装
4. 如果提示"未知来源"，需要在设置中允许

---

## 🔄 日常使用流程

### 修改代码后重新构建

```bash
# 1. 修改代码
# 2. 提交更改
git add .
git commit -m "描述你的修改"
git push

# 3. GitHub Actions自动开始构建
# 4. 5-10分钟后在Actions页面下载新的APK
```

### 创建版本Release

当你想发布一个正式版本：

```bash
# 1. 打标签
git tag v1.0.0
git push origin v1.0.0

# 2. GitHub Actions会自动创建Release
# 3. 在仓库的"Releases"页面可以看到
```

---

## 🐛 常见问题

### Q1: 构建失败 - Gradle下载超时

**原因**：GitHub服务器在国外，下载Gradle可能很慢

**解决方案**：
1. 在`gradle-wrapper.properties`中使用国内镜像：
```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.4-bin.zip
```

2. 或者在`.github/workflows/build-apk.yml`中添加缓存：
```yaml
- name: 缓存Gradle
  uses: actions/cache@v3
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
```

### Q2: 构建失败 - 找不到AndroidManifest.xml

**原因**：文件结构不正确

**解决方案**：
确保文件在正确位置：`app/src/main/AndroidManifest.xml`

### Q3: 构建失败 - 找不到资源文件

**原因**：缺少必需的资源文件

**解决方案**：
创建最小资源文件：
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/mipmap-*/ic_launcher.png`（可以暂时使用默认图标）

### Q4: 无法推送到GitHub - 认证失败

**原因**：GitHub不再支持密码认证

**解决方案**：
使用Personal Access Token：
1. GitHub -> Settings -> Developer settings -> Personal access tokens
2. Generate new token (classic)
3. 选择`repo`权限
4. 复制token（只显示一次！）
5. 推送时用token代替密码

### Q5: Actions没有自动运行

**原因**：可能是分支名不匹配

**解决方案**：
检查`.github/workflows/build-apk.yml`中的分支名：
```yaml
on:
  push:
    branches: [ main, master ]  # 确保包含你的分支名
```

---

## 📊 构建时间和资源

- **首次构建**：约10-15分钟（需要下载依赖）
- **后续构建**：约5-8分钟（有缓存）
- **GitHub Actions免费额度**：
  - 公开仓库：无限制
  - 私有仓库：每月2000分钟
  - 对于个人项目完全够用

---

## 🎉 完成！

现在你有了：
- ✅ 完整的Android项目结构
- ✅ 自动化的GitHub Actions构建
- ✅ 无需本地Android开发环境
- ✅ 每次推送自动生成APK
- ✅ 可以随时下载最新版本

**下次修改代码的流程：**
1. 在本地修改`.kt`文件
2. `git add . && git commit -m "修改说明" && git push`
3. 等待5-10分钟
4. 在GitHub Actions下载新APK
5. 安装到手机测试

享受自动化构建的便利！🚀
