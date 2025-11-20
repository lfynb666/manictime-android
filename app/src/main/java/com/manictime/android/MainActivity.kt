package com.manictime.android

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * ManicTime Android 主界面
 * 提供服务器配置、权限管理和服务控制
 */
class MainActivity : ComponentActivity() {
    
    private lateinit var prefs: ManicTimePreferences
    private lateinit var apiClient: ManicTimeApiClient
    
    private val screenshotPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val intent = Intent(this, ManicTimeService::class.java).apply {
                    action = ManicTimeService.ACTION_START_SCREENSHOT
                    putExtra(ManicTimeService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ManicTimeService.EXTRA_RESULT_DATA, data)
                }
                startService(intent)
                Toast.makeText(this, "截图功能已启用", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "需要截图权限才能启用截图功能", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = ManicTimePreferences(this)
        apiClient = ManicTimeApiClient(prefs)
        
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        
        var serverUrl by remember { mutableStateOf(prefs.serverUrl) }
        var username by remember { mutableStateOf(prefs.username) }
        var password by remember { mutableStateOf(prefs.password) }
        var isConnecting by remember { mutableStateOf(false) }
        var isAuthenticated by remember { mutableStateOf(prefs.isAuthenticated()) }
        var serviceRunning by remember { mutableStateOf(ManicTimeService.isRunning) }
        
        // 设置状态 - 使用remember确保UI更新
        var screenshotEnabled by remember { mutableStateOf(prefs.screenshotEnabled) }
        var screenshotInterval by remember { mutableStateOf(prefs.screenshotInterval) }
        var activityInterval by remember { mutableStateOf(prefs.activityInterval) }
        var autoStartEnabled by remember { mutableStateOf(prefs.autoStartEnabled) }
        var testScreenshotResult by remember { mutableStateOf<String?>(null) }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("ManicTime Android") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 状态卡片
                StatusCard(isAuthenticated, serviceRunning)
                
                // 服务器配置卡片
                ServerConfigCard(
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    isConnecting = isConnecting,
                    onServerUrlChange = { serverUrl = it },
                    onUsernameChange = { username = it },
                    onPasswordChange = { password = it },
                    onConnect = {
                        scope.launch {
                            isConnecting = true
                            try {
                                prefs.serverUrl = serverUrl
                                prefs.username = username
                                prefs.password = password
                                
                                val token = apiClient.authenticate()
                                prefs.accessToken = token
                                isAuthenticated = true
                                Toast.makeText(context, "连接成功!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "连接失败: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isConnecting = false
                            }
                        }
                    }
                )
                
                // 权限卡片
                PermissionsCard(
                    onRequestScreenshot = {
                        requestScreenshotPermission()
                    }
                )
                
                // 服务控制卡片
                ServiceControlCard(
                    serviceRunning = serviceRunning,
                    isAuthenticated = isAuthenticated,
                    onStartService = {
                        if (!isAuthenticated) {
                            Toast.makeText(context, "请先连接服务器", Toast.LENGTH_SHORT).show()
                            return@ServiceControlCard
                        }
                        
                        if (!hasUsageStatsPermission()) {
                            Toast.makeText(context, "请先授予应用使用统计权限", Toast.LENGTH_SHORT).show()
                            return@ServiceControlCard
                        }
                        
                        val intent = Intent(context, ManicTimeService::class.java).apply {
                            action = ManicTimeService.ACTION_START
                        }
                        startService(intent)
                        serviceRunning = true
                        Toast.makeText(context, "服务已启动", Toast.LENGTH_SHORT).show()
                    },
                    onStopService = {
                        val intent = Intent(context, ManicTimeService::class.java).apply {
                            action = ManicTimeService.ACTION_STOP
                        }
                        startService(intent)
                        serviceRunning = false
                        Toast.makeText(context, "服务已停止", Toast.LENGTH_SHORT).show()
                    }
                )
                
                // 设置卡片
                SettingsCard(
                    screenshotEnabled = screenshotEnabled,
                    screenshotInterval = screenshotInterval,
                    activityInterval = activityInterval,
                    autoStartEnabled = autoStartEnabled,
                    onScreenshotEnabledChange = { 
                        screenshotEnabled = it
                        prefs.screenshotEnabled = it
                    },
                    onScreenshotIntervalChange = { 
                        screenshotInterval = it
                        prefs.screenshotInterval = it
                    },
                    onActivityIntervalChange = { 
                        activityInterval = it
                        prefs.activityInterval = it
                    },
                    onAutoStartEnabledChange = {
                        autoStartEnabled = it
                        prefs.autoStartEnabled = it
                        if (it) {
                            Toast.makeText(context, "已启用开机自启动和服务保活", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                
                // 测试功能卡片
                TestCard(
                    onTestScreenshot = {
                        scope.launch {
                            try {
                                val result = testScreenshotCapture()
                                testScreenshotResult = result
                            } catch (e: Exception) {
                                testScreenshotResult = "测试失败: ${e.message}"
                            }
                        }
                    },
                    testResult = testScreenshotResult,
                    onDismissResult = { testScreenshotResult = null }
                )
            }
        }
    }
    
    @Composable
    fun StatusCard(isAuthenticated: Boolean, serviceRunning: Boolean) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (serviceRunning) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "状态",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isAuthenticated) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isAuthenticated) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = if (isAuthenticated) "已连接服务器" else "未连接",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (serviceRunning) Icons.Default.PlayArrow else Icons.Default.Stop,
                        contentDescription = null,
                        tint = if (serviceRunning) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (serviceRunning) "监控运行中" else "监控已停止",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
    
    @Composable
    fun ServerConfigCard(
        serverUrl: String,
        username: String,
        password: String,
        isConnecting: Boolean,
        onServerUrlChange: (String) -> Unit,
        onUsernameChange: (String) -> Unit,
        onPasswordChange: (String) -> Unit,
        onConnect: () -> Unit
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "服务器配置",
                    style = MaterialTheme.typography.titleMedium
                )
                
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://192.168.1.100:8080") },
                    leadingIcon = { Icon(Icons.Default.Cloud, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("用户名") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("密码") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Button(
                    onClick = onConnect,
                    enabled = !isConnecting && serverUrl.isNotEmpty() && 
                             username.isNotEmpty() && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isConnecting) "连接中..." else "连接服务器")
                }
            }
        }
    }
    
    @Composable
    fun PermissionsCard(onRequestScreenshot: () -> Unit) {
        val context = LocalContext.current
        val hasUsageStats = hasUsageStatsPermission()
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "权限管理",
                    style = MaterialTheme.typography.titleMedium
                )
                
                // 应用使用统计权限
                PermissionItem(
                    title = "应用使用统计",
                    description = "监控应用使用情况",
                    isGranted = hasUsageStats,
                    onRequest = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        startActivity(intent)
                    }
                )
                
                // 截图权限
                PermissionItem(
                    title = "截图权限",
                    description = "每次启动需要授权",
                    isGranted = false,
                    onRequest = onRequestScreenshot
                )
                
                // 电池优化
                PermissionItem(
                    title = "忽略电池优化",
                    description = "防止服务被杀死",
                    isGranted = false,
                    onRequest = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            startActivity(intent)
                        }
                    }
                )
            }
        }
    }
    
    @Composable
    fun PermissionItem(
        title: String,
        description: String,
        isGranted: Boolean,
        onRequest: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已授权",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(onClick = onRequest) {
                    Text("授权")
                }
            }
        }
    }
    
    @Composable
    fun ServiceControlCard(
        serviceRunning: Boolean,
        isAuthenticated: Boolean,
        onStartService: () -> Unit,
        onStopService: () -> Unit
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "服务控制",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onStartService,
                        enabled = !serviceRunning && isAuthenticated,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text("启动")
                    }
                    
                    Button(
                        onClick = onStopService,
                        enabled = serviceRunning,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(4.dp))
                        Text("停止")
                    }
                }
            }
        }
    }
    
    @Composable
    fun SettingsCard(
        screenshotEnabled: Boolean,
        screenshotInterval: Long,
        activityInterval: Long,
        autoStartEnabled: Boolean,
        onScreenshotEnabledChange: (Boolean) -> Unit,
        onScreenshotIntervalChange: (Long) -> Unit,
        onActivityIntervalChange: (Long) -> Unit,
        onAutoStartEnabledChange: (Boolean) -> Unit
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("开机自启动")
                        Text(
                            text = "像Clash一样保持服务运行",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = autoStartEnabled,
                        onCheckedChange = onAutoStartEnabledChange
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用截图")
                    Switch(
                        checked = screenshotEnabled,
                        onCheckedChange = onScreenshotEnabledChange
                    )
                }
                
                Column {
                    Text(
                        text = "截图间隔: ${screenshotInterval / 60000}分钟",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = (screenshotInterval / 60000).toFloat(),
                        onValueChange = { onScreenshotIntervalChange((it * 60000).toLong()) },
                        valueRange = 1f..30f,
                        steps = 28
                    )
                }
                
                Column {
                    Text(
                        text = "监控间隔: ${activityInterval / 1000}秒",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = (activityInterval / 1000).toFloat(),
                        onValueChange = { onActivityIntervalChange((it * 1000).toLong()) },
                        valueRange = 10f..120f,
                        steps = 21
                    )
                }
            }
        }
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    private fun requestScreenshotPermission() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenshotPermissionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
    
    @Composable
    fun TestCard(
        onTestScreenshot: () -> Unit,
        testResult: String?,
        onDismissResult: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🧪 测试功能",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Text(
                    text = "测试截图功能是否正常工作（不会上传到服务器）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
                
                Button(
                    onClick = onTestScreenshot,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Camera, null)
                    Spacer(Modifier.width(8.dp))
                    Text("测试截图")
                }
            }
        }
        
        // 显示测试结果对话框
        if (testResult != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = onDismissResult,
                title = { Text("测试结果") },
                text = { Text(testResult) },
                confirmButton = {
                    TextButton(onClick = onDismissResult) {
                        Text("确定")
                    }
                }
            )
        }
    }
    
    private suspend fun testScreenshotCapture(): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val result = StringBuilder()
        result.append("📸 测试MediaProjection截图\n")
        result.append("=" .repeat(40) + "\n\n")
        
        // 检查服务是否有MediaProjection
        if (!ManicTimeService.isRunning) {
            result.append("❌ 服务未运行\n")
            result.append("请先点击\"授权\"按钮获取截图权限\n")
            return@withContext result.toString()
        }
        
        result.append("✅ 服务运行中\n")
        result.append("正在尝试截图...\n\n")
        
        try {
            // 触发服务立即截图
            val intent = Intent(this@MainActivity, ManicTimeService::class.java).apply {
                action = "TEST_SCREENSHOT"
            }
            startService(intent)
            
            // 等待截图完成
            kotlinx.coroutines.delay(3000)
            
            // 检查截图目录
            val screenshotManager = ScreenshotManager(this@MainActivity)
            val screenshotsDir = screenshotManager.getScreenshotsDir()
            
            result.append("📂 截图目录: ${screenshotsDir.absolutePath}\n\n")
            
            if (!screenshotsDir.exists()) {
                result.append("❌ 截图目录不存在\n")
                return@withContext result.toString()
            }
            
            val files = screenshotsDir.listFiles()?.sortedByDescending { it.lastModified() }
            
            if (files.isNullOrEmpty()) {
                result.append("❌ 没有找到截图文件\n")
                result.append("\n可能原因:\n")
                result.append("1. 未授予截图权限\n")
                result.append("2. MediaProjection未初始化\n")
                result.append("3. 截图保存失败\n")
            } else {
                result.append("✅ 找到 ${files.size} 个文件\n\n")
                
                // 显示最新的3个文件
                files.take(3).forEach { file ->
                    result.append("📄 ${file.name}\n")
                    result.append("   大小: ${file.length() / 1024}KB\n")
                    result.append("   时间: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(file.lastModified())}\n\n")
                }
                
                result.append("🎉 截图功能正常！\n")
            }
            
        } catch (e: Exception) {
            result.append("❌ 测试失败: ${e.message}\n")
        }
        
        return@withContext result.toString()
    }
    
    private suspend fun testScreenshotCaptureOld(): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val result = StringBuilder()
        result.append("📸 测试screencap命令\n")
        result.append("=" .repeat(40) + "\n\n")
        
        val timestamp = System.currentTimeMillis()
        val cacheDir = externalCacheDir ?: cacheDir
        val screenshotManager = ScreenshotManager(this@MainActivity)
        var successMethod: String? = null
        var successFile: java.io.File? = null
        
        // ========== 方法1: screencap直接执行 ==========
        result.append("📸 方法1: screencap直接执行\n")
        try {
            val file1 = java.io.File(cacheDir, "test_method1_$timestamp.png")
            val process1 = ProcessBuilder()
                .command("screencap", "-p", file1.absolutePath)
                .redirectErrorStream(true)
                .start()
            val exitCode1 = process1.waitFor()
            val output1 = process1.inputStream.bufferedReader().readText()
            
            result.append("   exitCode: $exitCode1\n")
            if (output1.isNotEmpty()) result.append("   输出: ${output1.take(100)}\n")
            
            if (file1.exists() && file1.length() > 0) {
                result.append("   ✅ 成功! 大小: ${file1.length() / 1024}KB\n")
                successMethod = "方法1"
                successFile = file1
            } else {
                result.append("   ❌ 失败\n")
                file1.delete()
            }
        } catch (e: Exception) {
            result.append("   ❌ 异常: ${e.message}\n")
        }
        result.append("\n")
        
        // ========== 方法2: screencap通过sh ==========
        if (successFile == null) {
            result.append("📸 方法2: screencap通过sh\n")
            try {
                val file2 = java.io.File(cacheDir, "test_method2_$timestamp.png")
                val process2 = Runtime.getRuntime().exec(arrayOf(
                    "sh", "-c", "screencap -p ${file2.absolutePath}"
                ))
                val exitCode2 = process2.waitFor()
                
                result.append("   exitCode: $exitCode2\n")
                
                if (file2.exists() && file2.length() > 0) {
                    result.append("   ✅ 成功! 大小: ${file2.length() / 1024}KB\n")
                    successMethod = "方法2"
                    successFile = file2
                } else {
                    result.append("   ❌ 失败\n")
                    file2.delete()
                }
            } catch (e: Exception) {
                result.append("   ❌ 异常: ${e.message}\n")
            }
            result.append("\n")
        }
        
        // ========== 方法3: screencap输出到stdout再重定向 ==========
        if (successFile == null) {
            result.append("📸 方法3: screencap输出到stdout\n")
            try {
                val file3 = java.io.File(cacheDir, "test_method3_$timestamp.png")
                val process3 = Runtime.getRuntime().exec("screencap -p")
                val imageData = process3.inputStream.readBytes()
                process3.waitFor()
                
                if (imageData.isNotEmpty()) {
                    file3.writeBytes(imageData)
                    result.append("   数据大小: ${imageData.size / 1024}KB\n")
                    
                    if (file3.exists() && file3.length() > 0) {
                        result.append("   ✅ 成功! 大小: ${file3.length() / 1024}KB\n")
                        successMethod = "方法3"
                        successFile = file3
                    } else {
                        result.append("   ❌ 文件写入失败\n")
                        file3.delete()
                    }
                } else {
                    result.append("   ❌ 无数据输出\n")
                }
            } catch (e: Exception) {
                result.append("   ❌ 异常: ${e.message}\n")
            }
            result.append("\n")
        }
        
        // ========== 方法4: su权限执行screencap ==========
        if (successFile == null) {
            result.append("📸 方法4: su权限执行screencap (需要ROOT)\n")
            try {
                val file4 = java.io.File(cacheDir, "test_method4_$timestamp.png")
                val process4 = Runtime.getRuntime().exec(arrayOf(
                    "su", "-c", "screencap -p ${file4.absolutePath}"
                ))
                val exitCode4 = process4.waitFor()
                
                result.append("   exitCode: $exitCode4\n")
                
                if (file4.exists() && file4.length() > 0) {
                    result.append("   ✅ 成功! 大小: ${file4.length() / 1024}KB\n")
                    result.append("   ⚠️ 设备已ROOT\n")
                    successMethod = "方法4 (ROOT)"
                    successFile = file4
                } else {
                    result.append("   ❌ 失败 (设备可能未ROOT)\n")
                    file4.delete()
                }
            } catch (e: Exception) {
                result.append("   ❌ 异常: ${e.message}\n")
            }
            result.append("\n")
        }
        
        // ========== 方法5: /system/bin/screencap完整路径 ==========
        if (successFile == null) {
            result.append("📸 方法5: 使用完整路径\n")
            try {
                val file5 = java.io.File(cacheDir, "test_method5_$timestamp.png")
                val process5 = Runtime.getRuntime().exec(
                    "/system/bin/screencap -p ${file5.absolutePath}"
                )
                val exitCode5 = process5.waitFor()
                
                result.append("   exitCode: $exitCode5\n")
                
                if (file5.exists() && file5.length() > 0) {
                    result.append("   ✅ 成功! 大小: ${file5.length() / 1024}KB\n")
                    successMethod = "方法5"
                    successFile = file5
                } else {
                    result.append("   ❌ 失败\n")
                    file5.delete()
                }
            } catch (e: Exception) {
                result.append("   ❌ 异常: ${e.message}\n")
            }
            result.append("\n")
        }
        
        // ========== 方法6: 检查screencap是否存在 ==========
        result.append("📸 方法6: 检查screencap命令\n")
        try {
            val whichProcess = Runtime.getRuntime().exec("which screencap")
            val screencapPath = whichProcess.inputStream.bufferedReader().readText().trim()
            whichProcess.waitFor()
            
            if (screencapPath.isNotEmpty()) {
                result.append("   screencap路径: $screencapPath\n")
                
                // 检查文件权限
                val lsProcess = Runtime.getRuntime().exec("ls -l $screencapPath")
                val permissions = lsProcess.inputStream.bufferedReader().readText().trim()
                lsProcess.waitFor()
                result.append("   权限: $permissions\n")
            } else {
                result.append("   ❌ 找不到screencap命令\n")
            }
        } catch (e: Exception) {
            result.append("   ❌ 异常: ${e.message}\n")
        }
        result.append("\n")
        
        // ========== 方法7: 检查当前进程权限 ==========
        result.append("📸 方法7: 检查当前进程信息\n")
        try {
            val uid = android.os.Process.myUid()
            val pid = android.os.Process.myPid()
            result.append("   UID: $uid\n")
            result.append("   PID: $pid\n")
            result.append("   包名: ${packageName}\n")
            
            // 检查SELinux状态
            val selinuxProcess = Runtime.getRuntime().exec("getenforce")
            val selinuxStatus = selinuxProcess.inputStream.bufferedReader().readText().trim()
            selinuxProcess.waitFor()
            result.append("   SELinux: $selinuxStatus\n")
        } catch (e: Exception) {
            result.append("   ❌ 异常: ${e.message}\n")
        }
        result.append("\n")
        
        // ========== 总结 ==========
        result.append("=" .repeat(40) + "\n")
        result.append("📊 测试总结\n\n")
        
        if (successFile != null && successMethod != null) {
            result.append("🎉 找到可用方法: $successMethod\n\n")
            
            // 保存截图
            result.append("💾 正在保存截图...\n")
            val savedResult = screenshotManager.saveScreenshot(successFile)
            
            if (savedResult != null) {
                val (originalFile, thumbnailFile) = savedResult
                result.append("✅ 截图保存成功！\n\n")
                result.append("📄 原图: ${originalFile.name}\n")
                result.append("   大小: ${originalFile.length() / 1024}KB\n")
                result.append("📄 缩略图: ${thumbnailFile.name}\n")
                result.append("   大小: ${thumbnailFile.length() / 1024}KB\n\n")
                result.append("📂 保存路径:\n${screenshotManager.getScreenshotsDir().absolutePath}\n\n")
                result.append("✨ 建议: 在代码中使用 $successMethod")
            } else {
                result.append("❌ 保存失败")
            }
            
            // 清理临时文件
            successFile.delete()
        } else {
            result.append("❌ 所有方法都失败了\n\n")
            result.append("可能原因:\n")
            result.append("1. 设备不支持screencap命令\n")
            result.append("2. 需要ROOT权限\n")
            result.append("3. SELinux策略阻止\n")
            result.append("4. 需要使用MediaProjection API\n\n")
            result.append("💡 建议: 使用MediaProjection API\n")
            result.append("   (需要用户授权，但最可靠)")
        }
        
        return@withContext result.toString()
    }
}
