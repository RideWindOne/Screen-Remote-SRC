package com.screen.remote.android.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 伪装计算器 Activity
 * 单一界面：正常计算 + 输入特殊代码触发密码设置/验证
 * - 输入 *#772373# 按 = → 设置密码
 * - 输入 *#06# + 密码 按 = → 验证密码，正确进入远控，错误保持计算器
 */
class CalculatorActivity : ComponentActivity() {

    private val PREFS_NAME = "calculator_prefs"
    private val KEY_PASSWORD = "calculator_password"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF1A1A2E)
                ) {
                    CalculatorScreen(
                        onUnlock = { launchMainActivity() },
                        getSavedPassword = { getPassword() },
                        setSavedPassword = { setPassword(it) }
                    )
                }
            }
        }
    }

    private fun getPassword(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PASSWORD, "") ?: ""
    }

    private fun setPassword(password: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PASSWORD, password).apply()
    }

    private fun launchMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}

private const val SETUP_CODE = "*#772373#"
private const val UNLOCK_PREFIX = "*#06#"

@Composable
fun CalculatorScreen(
    onUnlock: () -> Unit,
    getSavedPassword: () -> String,
    setSavedPassword: (String) -> Unit
) {
    var display by remember { mutableStateOf("0") }
    var showSetupDialog by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var setupError by remember { mutableStateOf("") }

    fun onCharInput(ch: String) {
        if (display == "0" || display == "Error" || display.contains("=")) display = ""
        display += ch
    }

    fun onClear() {
        display = "0"
    }

    fun onBackspace() {
        if (display.isNotEmpty()) {
            display = display.dropLast(1)
            if (display.isEmpty()) display = "0"
        }
    }

    fun onEquals() {
        val unlockPrefix = when {
            display.startsWith("*#06#") -> "*#06#"
            display.startsWith("#06#") -> "#06#"
            else -> null
        }
        when {
            display == SETUP_CODE || display == "#772373#" -> {
                showSetupDialog = true
                display = "0"
            }
            unlockPrefix != null -> {
                val password = display.removePrefix(unlockPrefix)
                val saved = getSavedPassword()
                if (saved.isNotEmpty() && password.isNotEmpty() && password == saved) {
                    onUnlock()
                } else {
                    display = "0"
                }
            }
            else -> {
                val expression = display
                val result = tryCalculate(expression)
                if (result != null) {
                    display = "$expression=$result"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 显示区域 - 占满剩余空间
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF16213E))
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = display,
                color = Color(0xFFEAEAEA),
                fontSize = 40.sp,
                lineHeight = 52.sp,
                fontWeight = FontWeight.Light,
                maxLines = 4,
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 键盘 - 固定大小
        CalculatorKeypad(
            onChar = { onCharInput(it) },
            onClear = { onClear() },
            onBackspace = { onBackspace() },
            onEquals = { onEquals() }
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    // 设置密码对话框（二次确认）
    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = {
                showSetupDialog = false
                newPassword = ""
                confirmPassword = ""
                setupError = ""
            },
            title = { Text("设置密码", color = Color.White) },
            text = {
                Column {
                    Text(
                        text = "请输入新密码（数字组合）",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it.filter { ch -> ch.isDigit() }
                            setupError = ""
                        },
                        placeholder = { Text("输入密码") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it.filter { ch -> ch.isDigit() }
                            setupError = ""
                        },
                        placeholder = { Text("确认密码") },
                        singleLine = true
                    )
                    if (setupError.isNotEmpty()) {
                        Text(
                            text = setupError,
                            color = Color(0xFFE94560),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        newPassword.isEmpty() -> setupError = "密码不能为空"
                        newPassword != confirmPassword -> setupError = "两次输入的密码不一致"
                        else -> {
                            setSavedPassword(newPassword)
                            newPassword = ""
                            confirmPassword = ""
                            setupError = ""
                            showSetupDialog = false
                        }
                    }
                }) {
                    Text("确定", color = Color(0xFF4FC3F7))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    newPassword = ""
                    confirmPassword = ""
                    setupError = ""
                    showSetupDialog = false
                }) {
                    Text("取消", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF16213E)
        )
    }
}

@Composable
fun CalculatorKeypad(
    onChar: (String) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onEquals: () -> Unit
) {
    val buttons = listOf(
        listOf("C", "⌫", "*", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "#", "=")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { label ->
                    val isOperator = label in listOf("÷", "×", "-", "+", "=")
                    val isFunction = label in listOf("C", "⌫")
                    val isSpecial = label in listOf("*", "#")

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    label == "=" -> Color(0xFF4FC3F7)
                                    isOperator -> Color(0xFF0F3460)
                                    isFunction || isSpecial -> Color(0xFF2D3A5F)
                                    else -> Color(0xFF1A1A2E)
                                }
                            )
                            .clickable {
                                when (label) {
                                    "C" -> onClear()
                                    "⌫" -> onBackspace()
                                    "=" -> onEquals()
                                    else -> onChar(label)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (label == "=") Color(0xFF1A1A2E) else Color(0xFFEAEAEA),
                            fontSize = 24.sp,
                            fontWeight = if (isOperator || isFunction || isSpecial) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

private fun tryCalculate(expression: String): String? {
    val operators = listOf("+", "-", "×", "÷")
    for (op in operators) {
        val index = expression.indexOf(op)
        if (index > 0 && index < expression.length - 1) {
            val a = expression.substring(0, index).toDoubleOrNull()
            val b = expression.substring(index + 1).toDoubleOrNull()
            if (a != null && b != null) {
                val result = when (op) {
                    "+" -> a + b
                    "-" -> a - b
                    "×" -> a * b
                    "÷" -> if (b != 0.0) a / b else return null
                    else -> return null
                }
                return formatResult(result)
            }
        }
    }
    return null
}

private fun formatResult(d: Double): String {
    if (d.isNaN()) return "Error"
    if (d == d.toLong().toDouble()) return d.toLong().toString()
    return String.format("%.8f", d).trimEnd('0').trimEnd('.')
}
