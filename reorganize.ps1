# ManicTime Android 项目重组脚本
# 使用方法: 在项目根目录打开PowerShell，运行 .\reorganize.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  ManicTime Android 项目结构重组工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 确认操作
Write-Host "⚠️  此脚本将重组项目结构，建议先备份！" -ForegroundColor Yellow
$confirm = Read-Host "是否继续? (y/n)"
if ($confirm -ne 'y') {
    Write-Host "操作已取消" -ForegroundColor Red
    exit
}

Write-Host "`n开始重组..." -ForegroundColor Green

# 1. 创建目录结构
Write-Host "`n[1/8] 创建目录结构..." -ForegroundColor Yellow
$directories = @(
    "app\src\main\java\com\manictime\android",
    "app\src\main\res\values",
    "app\src\main\res\mipmap-hdpi",
    "app\src\main\res\mipmap-mdpi",
    "app\src\main\res\mipmap-xhdpi",
    "app\src\main\res\mipmap-xxhdpi",
    "app\src\main\res\mipmap-xxxhdpi",
    "gradle\wrapper"
)

foreach ($dir in $directories) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Write-Host "  ✓ 创建 $dir" -ForegroundColor Gray
}

# 2. 移动Kotlin文件
Write-Host "`n[2/8] 移动Kotlin源文件..." -ForegroundColor Yellow
$kotlinFiles = @("MainActivity.kt", "ManicTimeServer.kt", "ManicTimeApiClient.kt", "ManicTimePreferences.kt")
foreach ($file in $kotlinFiles) {
    if (Test-Path $file) {
        Move-Item -Path $file -Destination "app\src\main\java\com\manictime\android\" -Force
        Write-Host "  ✓ 移动 $file" -ForegroundColor Gray
    } else {
        Write-Host "  ⚠ 未找到 $file" -ForegroundColor Yellow
    }
}

# 3. 移动AndroidManifest.xml
Write-Host "`n[3/8] 移动AndroidManifest.xml..." -ForegroundColor Yellow
if (Test-Path "AndroidManifest.xml") {
    Move-Item -Path "AndroidManifest.xml" -Destination "app\src\main\" -Force
    Write-Host "  ✓ 移动 AndroidManifest.xml" -ForegroundColor Gray
}

# 4. 移动app的build.gradle.kts
Write-Host "`n[4/8] 移动build.gradle.kts到app目录..." -ForegroundColor Yellow
if (Test-Path "build.gradle.kts") {
    Move-Item -Path "build.gradle.kts" -Destination "app\" -Force
    Write-Host "  ✓ 移动 build.gradle.kts" -ForegroundColor Gray
}

# 5. 创建strings.xml
Write-Host "`n[5/8] 创建strings.xml..." -ForegroundColor Yellow
$stringsXml = @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">ManicTime</string>
</resources>
"@
Set-Content -Path "app\src\main\res\values\strings.xml" -Value $stringsXml -Encoding UTF8
Write-Host "  ✓ 创建 strings.xml" -ForegroundColor Gray

# 6. 创建项目级build.gradle.kts
Write-Host "`n[6/8] 创建项目级build.gradle.kts..." -ForegroundColor Yellow
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
Set-Content -Path "build.gradle.kts" -Value $rootBuildGradle -Encoding UTF8
Write-Host "  ✓ 创建 build.gradle.kts" -ForegroundColor Gray

# 7. 创建gradle-wrapper.properties
Write-Host "`n[7/8] 创建gradle-wrapper.properties..." -ForegroundColor Yellow
$gradleWrapperProps = @"
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"@
Set-Content -Path "gradle\wrapper\gradle-wrapper.properties" -Value $gradleWrapperProps -Encoding UTF8
Write-Host "  ✓ 创建 gradle-wrapper.properties" -ForegroundColor Gray

# 8. 下载Gradle Wrapper文件
Write-Host "`n[8/8] 下载Gradle Wrapper文件..." -ForegroundColor Yellow
Write-Host "  正在下载 gradle-wrapper.jar..." -ForegroundColor Gray

try {
    $wrapperJarUrl = "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar"
    Invoke-WebRequest -Uri $wrapperJarUrl -OutFile "gradle\wrapper\gradle-wrapper.jar"
    Write-Host "  ✓ 下载 gradle-wrapper.jar" -ForegroundColor Gray
} catch {
    Write-Host "  ⚠ 下载失败，请手动下载" -ForegroundColor Yellow
}

Write-Host "  正在下载 gradlew..." -ForegroundColor Gray
try {
    $gradlewUrl = "https://raw.githubusercontent.com/gradle/gradle/master/gradlew"
    Invoke-WebRequest -Uri $gradlewUrl -OutFile "gradlew"
    Write-Host "  ✓ 下载 gradlew" -ForegroundColor Gray
} catch {
    Write-Host "  ⚠ 下载失败，请手动下载" -ForegroundColor Yellow
}

Write-Host "  正在下载 gradlew.bat..." -ForegroundColor Gray
try {
    $gradlewBatUrl = "https://raw.githubusercontent.com/gradle/gradle/master/gradlew.bat"
    Invoke-WebRequest -Uri $gradlewBatUrl -OutFile "gradlew.bat"
    Write-Host "  ✓ 下载 gradlew.bat" -ForegroundColor Gray
} catch {
    Write-Host "  ⚠ 下载失败，请手动下载" -ForegroundColor Yellow
}

# 完成
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  ✅ 项目结构重组完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`n📋 项目结构：" -ForegroundColor Cyan
Write-Host "  app/" -ForegroundColor White
Write-Host "    └── src/main/" -ForegroundColor White
Write-Host "        ├── java/com/manictime/android/" -ForegroundColor White
Write-Host "        │   ├── MainActivity.kt" -ForegroundColor Gray
Write-Host "        │   ├── ManicTimeService.kt" -ForegroundColor Gray
Write-Host "        │   ├── ManicTimeApiClient.kt" -ForegroundColor Gray
Write-Host "        │   └── ManicTimePreferences.kt" -ForegroundColor Gray
Write-Host "        ├── res/values/strings.xml" -ForegroundColor Gray
Write-Host "        └── AndroidManifest.xml" -ForegroundColor Gray

Write-Host "`n🚀 下一步操作：" -ForegroundColor Cyan
Write-Host "  1. 验证文件结构：" -ForegroundColor White
Write-Host "     dir app\src\main\java\com\manictime\android" -ForegroundColor Gray
Write-Host ""
Write-Host "  2. 初始化Git（如果还没有）：" -ForegroundColor White
Write-Host "     git init" -ForegroundColor Gray
Write-Host "     git add ." -ForegroundColor Gray
Write-Host "     git commit -m 'Initial commit: Reorganized project structure'" -ForegroundColor Gray
Write-Host ""
Write-Host "  3. 推送到GitHub：" -ForegroundColor White
Write-Host "     git remote add origin https://github.com/YOUR_USERNAME/manictime-android.git" -ForegroundColor Gray
Write-Host "     git branch -M main" -ForegroundColor Gray
Write-Host "     git push -u origin main" -ForegroundColor Gray
Write-Host ""
Write-Host "  4. 查看GITHUB_GUIDE.md了解详细步骤" -ForegroundColor White
Write-Host ""

Write-Host "⚠️  注意事项：" -ForegroundColor Yellow
Write-Host "  • 如果Gradle Wrapper下载失败，请查看PROJECT_STRUCTURE.md手动下载" -ForegroundColor Gray
Write-Host "  • 推送到GitHub后，Actions会自动开始构建" -ForegroundColor Gray
Write-Host "  • 首次构建约需10-15分钟" -ForegroundColor Gray
Write-Host ""
