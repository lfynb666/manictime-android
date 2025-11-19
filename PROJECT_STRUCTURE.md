# 📁 项目结构重组指南

## 🎯 目标结构

```
manictime-android/
├── .github/
│   └── workflows/
│       └── build-apk.yml                    # ✅ 已存在
│
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/manictime/android/
│   │       │   ├── MainActivity.kt          # 需要移动
│   │       │   ├── ManicTimeService.kt      # 需要移动
│   │       │   ├── ManicTimeApiClient.kt    # 需要移动
│   │       │   └── ManicTimePreferences.kt  # 需要移动
│   │       │
│   │       ├── res/
│   │       │   ├── values/
│   │       │   │   └── strings.xml          # 需要创建
│   │       │   ├── mipmap-hdpi/
│   │       │   ├── mipmap-mdpi/
│   │       │   ├── mipmap-xhdpi/
│   │       │   ├── mipmap-xxhdpi/
│   │       │   └── mipmap-xxxhdpi/
│   │       │
│   │       └── AndroidManifest.xml          # 需要移动
│   │
│   └── build.gradle.kts                     # 需要移动
│
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar               # 需要下载
│       └── gradle-wrapper.properties        # 需要创建
│
├── .gitignore                               # ✅ 已存在
├── build.gradle.kts                         # 需要创建（项目级）
├── settings.gradle.kts                      # ✅ 已存在
├── gradle.properties                        # ✅ 已存在
├── gradlew                                  # 需要下载
├── gradlew.bat                              # 需要下载
├── README.md                                # ✅ 已存在（Readme.md）
├── GITHUB_GUIDE.md                          # ✅ 刚创建
└── PROJECT_STRUCTURE.md                     # ✅ 当前文件
```

---

## 🔧 快速重组脚本

### Windows PowerShell脚本

将以下内容保存为`reorganize.ps1`，在项目根目录运行：

```powershell
# ManicTime Android 项目重组脚本

Write-Host "开始重组项目结构..." -ForegroundColor Green

# 1. 创建目录结构
Write-Host "`n创建目录结构..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path "app\src\main\java\com\manictime\android" | Out-Null
New-Item -ItemType Directory -Force -Path "app\src\main\res\values" | Out-Null
New-Item -ItemType Directory -Force -Path "app\src\main\res\mipmap-hdpi" | Out-Null
New-Item -ItemType Directory -Force -Path "app\src\main\res\mipmap-mdpi" | Out-Null
New-Item -ItemType Directory -Force -Path "app\src\main\res\mipmap-xhdpi" | Out-Null
New-Item -ItemType Directory -Force -Path "app\src\main\res\mipmap-xxhdpi" | Out-Null
New-Item -ItemType Directory -Force -Path "app\src\main\res\mipmap-xxxhdpi" | Out-Null
New-Item -ItemType Directory -Force -Path "gradle\wrapper" | Out-Null

# 2. 移动Kotlin文件
Write-Host "`n移动Kotlin源文件..." -ForegroundColor Yellow
Move-Item -Path "MainActivity.kt" -Destination "app\src\main\java\com\manictime\android\" -Force
Move-Item -Path "ManicTimeServer.kt" -Destination "app\src\main\java\com\manictime\android\" -Force
Move-Item -Path "ManicTimeApiClient.kt" -Destination "app\src\main\java\com\manictime\android\" -Force
Move-Item -Path "ManicTimePreferences.kt" -Destination "app\src\main\java\com\manictime\android\" -Force

# 3. 移动AndroidManifest.xml
Write-Host "`n移动AndroidManifest.xml..." -ForegroundColor Yellow
Move-Item -Path "AndroidManifest.xml" -Destination "app\src\main\" -Force

# 4. 移动app的build.gradle.kts
Write-Host "`n移动build.gradle.kts到app目录..." -ForegroundColor Yellow
Move-Item -Path "build.gradle.kts" -Destination "app\" -Force

# 5. 创建strings.xml
Write-Host "`n创建strings.xml..." -ForegroundColor Yellow
$stringsXml = @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">ManicTime</string>
</resources>
"@
Set-Content -Path "app\src\main\res\values\strings.xml" -Value $stringsXml

# 6. 创建项目级build.gradle.kts
Write-Host "`n创建项目级build.gradle.kts..." -ForegroundColor Yellow
$rootBuildGradle = @"
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "1.9.20" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
"@
Set-Content -Path "build.gradle.kts" -Value $rootBuildGradle

# 7. 创建gradle-wrapper.properties
Write-Host "`n创建gradle-wrapper.properties..." -ForegroundColor Yellow
$gradleWrapperProps = @"
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"@
Set-Content -Path "gradle\wrapper\gradle-wrapper.properties" -Value $gradleWrapperProps

Write-Host "`n✅ 项目结构重组完成！" -ForegroundColor Green
Write-Host "`n⚠️  还需要手动完成以下步骤：" -ForegroundColor Yellow
Write-Host "1. 下载Gradle Wrapper文件：" -ForegroundColor Cyan
Write-Host "   - gradlew" -ForegroundColor White
Write-Host "   - gradlew.bat" -ForegroundColor White
Write-Host "   - gradle\wrapper\gradle-wrapper.jar" -ForegroundColor White
Write-Host "`n2. 下载地址：" -ForegroundColor Cyan
Write-Host "   https://github.com/gradle/gradle/tree/master/gradle/wrapper" -ForegroundColor White
Write-Host "`n3. 或者从任何Android项目复制这些文件" -ForegroundColor Cyan
Write-Host "`n4. 完成后执行：" -ForegroundColor Cyan
Write-Host "   git add ." -ForegroundColor White
Write-Host "   git commit -m 'Reorganize project structure'" -ForegroundColor White
Write-Host "   git push" -ForegroundColor White
```

### 使用方法

```powershell
# 在项目根目录打开PowerShell
cd c:\Users\37666\manictimeapp

# 运行脚本
.\reorganize.ps1
```

---

## 📥 下载Gradle Wrapper文件

### 方法1：从官方下载（推荐）

1. 访问：https://services.gradle.org/distributions/gradle-8.4-all.zip
2. 下载并解压
3. 从解压的文件夹中复制：
   - `gradle/wrapper/gradle-wrapper.jar` → 你的项目`gradle/wrapper/`
   - `gradlew` → 你的项目根目录
   - `gradlew.bat` → 你的项目根目录

### 方法2：从现有Android项目复制

如果你有其他Android项目：
```powershell
# 从其他项目复制
copy "C:\path\to\other\android\project\gradlew" .
copy "C:\path\to\other\android\project\gradlew.bat" .
copy "C:\path\to\other\android\project\gradle\wrapper\gradle-wrapper.jar" gradle\wrapper\
```

### 方法3：使用Git克隆模板

```bash
# 克隆一个最小的Android项目模板
git clone https://github.com/android/gradle-recipes.git temp
copy temp\gradlew .
copy temp\gradlew.bat .
copy temp\gradle\wrapper\gradle-wrapper.jar gradle\wrapper\
rmdir /s /q temp
```

---

## ✅ 验证项目结构

重组完成后，运行以下检查：

```powershell
# 检查文件是否存在
Write-Host "检查项目结构..." -ForegroundColor Green

$requiredFiles = @(
    "app\src\main\java\com\manictime\android\MainActivity.kt",
    "app\src\main\java\com\manictime\android\ManicTimeService.kt",
    "app\src\main\java\com\manictime\android\ManicTimeApiClient.kt",
    "app\src\main\java\com\manictime\android\ManicTimePreferences.kt",
    "app\src\main\AndroidManifest.xml",
    "app\src\main\res\values\strings.xml",
    "app\build.gradle.kts",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "gradle\wrapper\gradle-wrapper.properties",
    "gradle\wrapper\gradle-wrapper.jar",
    "gradlew",
    "gradlew.bat",
    ".github\workflows\build-apk.yml"
)

foreach ($file in $requiredFiles) {
    if (Test-Path $file) {
        Write-Host "✅ $file" -ForegroundColor Green
    } else {
        Write-Host "❌ $file (缺失)" -ForegroundColor Red
    }
}
```

---

## 🚀 推送到GitHub

结构重组完成后：

```bash
# 1. 查看更改
git status

# 2. 添加所有文件
git add .

# 3. 提交
git commit -m "Reorganize project to standard Android structure"

# 4. 推送
git push origin main
```

---

## 📝 注意事项

1. **备份原始文件**：重组前建议备份整个目录
2. **Gradle Wrapper必须**：没有这些文件GitHub Actions无法构建
3. **文件路径大小写**：Linux系统区分大小写，确保路径正确
4. **图标文件**：暂时可以不添加，构建时会使用默认图标

---

## 🎯 下一步

完成项目重组后，参考`GITHUB_GUIDE.md`进行：
1. 推送到GitHub
2. 触发自动构建
3. 下载APK
4. 安装测试
