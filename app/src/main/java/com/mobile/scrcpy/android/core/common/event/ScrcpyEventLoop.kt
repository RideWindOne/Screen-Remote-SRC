package com.mobile.scrcpy.android.core.common.event

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scrcpy 事件循环
 *
 * 替代 SDL_WaitEvent 循环，使用 Kotlin Channel 实现
 * 线程安全的事件队列，支持跨线程通信
 * 
 * 作用域：会话级事件循环，由 ScrcpyEventBus 管理
 * 生命周期：随 ScrcpyEventBus 启动/停止
 *
 * 参考：docs/ScrcpyVS/07-sdl-event-system.md
 */
class ScrcpyEventLoop(
    private val scope: CoroutineScope,
) {
    private val eventChannel = Channel<ScrcpyEvent>(Channel.UNLIMITED)
    private var loopJob: Job? = null
    private var isRunning = false

    val eventHandlers = mutableMapOf<Class<out ScrcpyEvent>, (ScrcpyEvent) -> Unit>()

    companion object {
        private const val TAG = "ScrcpyEventLoop"
    }

    /**
     * 注册事件处理器
     */
    inline fun <reified T : ScrcpyEvent> on(noinline handler: (T) -> Unit) {
        eventHandlers[T::class.java] = { event ->
            @Suppress("UNCHECKED_CAST")
            handler(event as T)
        }
    }

    /**
     * 推送事件到队列（线程安全）
     *
     * 对应 SDL_PushEvent
     */
    fun pushEvent(event: ScrcpyEvent): Boolean = eventChannel.trySend(event).isSuccess

    /**
     * 启动事件循环
     *
     * 对应 SDL_WaitEvent 循环
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG, "Event loop already running")
            return
        }

        isRunning = true
        loopJob =
            scope.launch {
                Log.d(TAG, "Event loop started")

                try {
                    for (event in eventChannel) {
                        handleEvent(event)

                        // 退出事件
                        if (event is Quit) {
                            Log.d(TAG, "Quit event received, stopping loop")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Event loop error", e)
                } finally {
                    isRunning = false
                    Log.d(TAG, "Event loop stopped")
                }
            }
    }

    /**
     * 停止事件循环
     */
    fun stop() {
        if (!isRunning) return

        pushEvent(Quit)
        loopJob?.cancel()
        loopJob = null
        isRunning = false
    }

    /**
     * 处理单个事件
     */
    private suspend fun handleEvent(event: ScrcpyEvent) {
        // 记录事件日志
        ScrcpyEventLogger.logEvent(event)

        // 主线程任务特殊处理
        if (event is RunOnMainThread) {
            withContext(Dispatchers.Main) {
                try {
                    event.task()
                } catch (e: Exception) {
                    Log.e(TAG, "Error running task on main thread", e)
                }
            }
            return
        }

        // 查找并执行注册的处理器
        val handler = eventHandlers[event::class.java]
        if (handler != null) {
            try {
                handler(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling event: ${event::class.simpleName}", e)
            }
        } else {
            // 没有处理器的事件也记录（但不是错误）
            Log.v(TAG, "No handler for event: ${event::class.simpleName}")
        }
    }

    /**
     * 在主线程执行任务
     *
     * 对应 sc_post_to_main_thread
     */
    fun postToMainThread(task: () -> Unit): Boolean = pushEvent(RunOnMainThread(task))

    fun isRunning(): Boolean = isRunning
}
