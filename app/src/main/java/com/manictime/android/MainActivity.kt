package com.manictime.android

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
                PermissionsCard()
                
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
    fun PermissionsCard() {
        val context = LocalContext.current
        val hasUsageStats = hasUsageStatsPermission()
        val hasAccessibility = isAccessibilityServiceEnabled()
        
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
                    description = "监控应用使用时间",
                    isGranted = hasUsageStats,
                    onRequest = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        startActivity(intent)
                    }
                )
                
                // 辅助功能权限（用于自动截图）
                PermissionItem(
                    title = "辅助功能（自动截图）",
                    description = "一次授权永久有效，开机自动截图",
                    isGranted = hasAccessibility,
                    onRequest = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                        Toast.makeText(context, "请找到ManicTime并启用", Toast.LENGTH_LONG).show()
                    }
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
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${ScreenCaptureAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service) == true
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
        try {
            // 检查是否有截图权限
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) 
                as? MediaProjectionManager
                ?: return@withContext "❌ 无法获取MediaProjectionManager"
            
            // 检查存储权限
            val cacheDir = externalCacheDir ?: cacheDir
            val testFile = java.io.File(cacheDir, "test_screenshot_${System.currentTimeMillis()}.jpg")
            
            // 尝试创建测试文件
            if (testFile.createNewFile()) {
                testFile.delete()
                return@withContext "✅ 截图功能准备就绪\n\n" +
                    "保存路径: ${cacheDir.absolutePath}\n" +
                    "文件名格式: screenshot_时间戳.jpg\n\n" +
                    "⚠️ 注意: 实际截图需要先启动服务并授予屏幕录制权限"
            } else {
                return@withContext "❌ 无法创建文件，请检查存储权限"
            }
        } catch (e: Exception) {
            return@withContext "❌ 测试失败: ${e.message}\n\n" +
                "可能原因:\n" +
                "1. 缺少存储权限\n" +
                "2. 存储空间不足\n" +
                "3. 系统限制"
        }
    }
}
