package com.mobile.scrcpy.android.infrastructure.scrcpy.connection

import com.mobile.scrcpy.android.core.domain.model.ConnectionProgress
import com.mobile.scrcpy.android.core.domain.model.ConnectionStep
import com.mobile.scrcpy.android.core.domain.model.StepStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 连接状态机
 * 管理连接过程中的各个步骤和状态
 */
class ConnectionStateMachine {
    private val _progress = MutableStateFlow<ConnectionProgress?>(null)
    val progress: StateFlow<ConnectionProgress?> = _progress.asStateFlow()

    private val _progressList = MutableStateFlow<List<ConnectionProgress>>(emptyList())
    val connectionProgress: StateFlow<List<ConnectionProgress>> = _progressList.asStateFlow()

    /**
     * 更新连接进度
     */
    fun updateProgress(
        step: ConnectionStep,
        status: StepStatus,
        message: String? = null,
        error: String? = null,
    ) {
        val newProgress =
            ConnectionProgress(
                step = step,
                status = status,
                message = message ?: getDefaultMessage(step, status),
                error = error,
            )
        _progress.value = newProgress

        // 更新进度列表
        val currentList = _progressList.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.step == step }
        if (existingIndex >= 0) {
            currentList[existingIndex] = newProgress
        } else {
            currentList.add(newProgress)
        }
        _progressList.value = currentList
    }

    /**
     * 重置状态
     */
    fun reset() {
        _progress.value = null
        _progressList.value = emptyList()
    }

    /**
     * 清空连接进度（别名方法）
     */
    fun clearProgress() {
        reset()
    }

    /**
     * 获取默认消息
     */
    private fun getDefaultMessage(
        step: ConnectionStep,
        status: StepStatus,
    ): String =
        when (step) {
            ConnectionStep.ADB_CONNECT -> {
                when (status) {
                    StepStatus.PENDING -> "Waiting to connect to ADB..."
                    StepStatus.RUNNING -> "Connecting to ADB..."
                    StepStatus.SUCCESS -> "ADB connected"
                    StepStatus.FAILED -> "ADB connection failed"
                }
            }

            ConnectionStep.ADB_FORWARD -> {
                when (status) {
                    StepStatus.PENDING -> "Waiting to set up port forwarding..."
                    StepStatus.RUNNING -> "Setting up port forwarding..."
                    StepStatus.SUCCESS -> "Port forwarding established"
                    StepStatus.FAILED -> "Port forwarding failed"
                }
            }

            ConnectionStep.PUSH_SERVER -> {
                when (status) {
                    StepStatus.PENDING -> "Waiting to push server..."
                    StepStatus.RUNNING -> "Pushing server..."
                    StepStatus.SUCCESS -> "Server pushed"
                    StepStatus.FAILED -> "Failed to push server"
                }
            }

            ConnectionStep.START_SERVER -> {
                when (status) {
                    StepStatus.PENDING -> "Waiting to start server..."
                    StepStatus.RUNNING -> "Starting server..."
                    StepStatus.SUCCESS -> "Server started"
                    StepStatus.FAILED -> "Failed to start server"
                }
            }

            ConnectionStep.CONNECT_SOCKET -> {
                when (status) {
                    StepStatus.PENDING -> "Waiting to connect socket..."
                    StepStatus.RUNNING -> "Connecting socket..."
                    StepStatus.SUCCESS -> "Socket connected"
                    StepStatus.FAILED -> "Socket connection failed"
                }
            }

            ConnectionStep.COMPLETED -> {
                when (status) {
                    StepStatus.PENDING -> "Waiting to complete..."
                    StepStatus.RUNNING -> "Completing..."
                    StepStatus.SUCCESS -> "Connection established"
                    StepStatus.FAILED -> "Connection failed"
                }
            }
        }
}
