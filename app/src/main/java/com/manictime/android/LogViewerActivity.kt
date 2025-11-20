package com.manictime.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class LogViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LogViewerScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onBack: () -> Unit) {
    var logs by remember { mutableStateOf(AppLogger.getLogs()) }
    val listState = rememberLazyListState()
    
    // 自动刷新日志
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            logs = AppLogger.getLogs()
        }
    }
    
    // 自动滚动到底部
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("实时日志 (${logs.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { AppLogger.clear() }) {
                        Icon(Icons.Default.Delete, "清空")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
            state = listState
        ) {
            items(logs) { log ->
                LogItem(log)
            }
        }
    }
}

@Composable
fun LogItem(log: AppLogger.LogEntry) {
    val color = when (log.level) {
        "E" -> Color.Red
        "W" -> Color(0xFFFF9800)
        "I" -> Color.Green
        else -> Color.Gray
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row {
            Text(
                text = log.level,
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.width(20.dp)
            )
            Text(
                text = log.time,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.width(80.dp)
            )
            Text(
                text = log.tag,
                color = Color.Blue,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.width(120.dp)
            )
        }
        Text(
            text = log.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 220.dp)
        )
        if (log.throwable != null) {
            Text(
                text = log.throwable,
                color = Color.Red,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 220.dp)
            )
        }
        Divider(modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * 全局日志收集器
 */
object AppLogger {
    data class LogEntry(
        val time: String,
        val level: String,
        val tag: String,
        val message: String,
        val throwable: String?
    )
    
    private val logs = mutableListOf<LogEntry>()
    private val maxLogs = 500
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    
    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        synchronized(logs) {
            logs.add(LogEntry(
                time = timeFormat.format(Date()),
                level = level,
                tag = tag,
                message = message,
                throwable = throwable?.stackTraceToString()
            ))
            
            // 限制日志数量
            if (logs.size > maxLogs) {
                logs.removeAt(0)
            }
        }
    }
    
    fun d(tag: String, message: String) = log("D", tag, message)
    fun i(tag: String, message: String) = log("I", tag, message)
    fun w(tag: String, message: String) = log("W", tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log("E", tag, message, throwable)
    
    fun getLogs(): List<LogEntry> = synchronized(logs) { logs.toList() }
    
    fun clear() = synchronized(logs) { logs.clear() }
}
