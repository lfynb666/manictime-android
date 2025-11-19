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

/**
 * ManicTime Android 主界面
 * 提供服务器配置、权限管理和服务控制
 */
class MainActivity : ComponentActivity() {
    
    private lateinit var prefs: ManicTimePreferences
    private lateinit var apiClient: ManicTimeApiClient
    
    // 截图权限请求
    private val screenshotPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                // 启动截图服务
                val intent = Intent(this, ManicTimeService::class.java).apply {
                    action = ManicTimeService.ACTION_START_SCREENSHOT
                    putExtra(ManicTimeService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ManicTimeService.EXTRA_RESULT_DATA, data)
                }
                startService(intent)
                Toast.makeText(this, "截图权限已授予", Toast.LENGTH_SHORT).show()
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
                    },
                    onRequestScreenshot = {
                        if (prefs.screenshotEnabled) {
                            requestScreenshotPermission()
                        } else {
                            Toast.makeText(context, "请先在设置中启用截图功能", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                
                // 设置卡片
                SettingsCard(
                    screenshotEnabled = prefs.screenshotEnabled,
                    screenshotInterval = prefs.screenshotInterval,
                    activityInterval = prefs.activityInterval,
                    onScreenshotEnabledChange = { prefs.screenshotEnabled = it },
                    onScreenshotIntervalChange = { prefs.screenshotInterval = it },
                    onActivityIntervalChange = { prefs.activityInterval = it }
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
                
                // 截图权限
                PermissionItem(
                    title = "屏幕截图",
                    description = "定时截取屏幕",
                    isGranted = false, // 每次都需要重新授予
                    onRequest = {
                        requestScreenshotPermission()
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
        onStopService: () -> Unit,
        onRequestScreenshot: () -> Unit
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
                
                Button(
                    onClick = onRequestScreenshot,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = serviceRunning
                ) {
                    Icon(Icons.Default.Screenshot, null)
                    Spacer(Modifier.width(8.dp))
                    Text("启用截图功能")
                }
            }
        }
    }
    
    @Composable
    fun SettingsCard(
        screenshotEnabled: Boolean,
        screenshotInterval: Long,
        activityInterval: Long,
        onScreenshotEnabledChange: (Boolean) -> Unit,
        onScreenshotIntervalChange: (Long) -> Unit,
        onActivityIntervalChange: (Long) -> Unit
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
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) 
            as MediaProjectionManager
        screenshotPermissionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}
