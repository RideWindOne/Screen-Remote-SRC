package com.mobile.scrcpy.android.core.common.event

/**
 * Scrcpy 事件系统使用示例
 * 
 * 参考 screen-remote-ios 的 SDL 事件机制实现
 * 
 * ## iOS 版本的 SDL 事件使用场景
 * 
 * ### 1. 退出 Scrcpy (ScrcpyClient.m:345)
 * ```objective-c
 * -(void)stopScrcpy {
 *     SDL_Event event;
 *     event.type = SDL_QUIT;
 *     SDL_PushEvent(&event);
 * }
 * ```
 * 
 * ### 2. 同步剪贴板 (ScrcpyClient.m:183)
 * ```objective-c
 * -(void)onStartOrResume {
 *     SDL_Event clip_event;
 *     clip_event.type = SDL_CLIPBOARDUPDATE;
 *     SDL_PushEvent(&clip_event);
 * }
 * ```
 * 
 * ### 3. 发送按键事件 (ScrcpyClient.m:521)
 * ```objective-c
 * -(void)sendKeycodeEvent:(SDL_Scancode)scancode 
 *                 keycode:(SDL_Keycode)keycode 
 *                  keymod:(SDL_Keymod)keymod {
 *     SDL_KeyboardEvent keyEvent;
 *     keyEvent.type = SDL_KEYDOWN;
 *     SDL_Event event;
 *     event.key = keyEvent;
 *     SDL_PushEvent(&event);
 * }
 * ```
 * 
 * ## Android 版本对应实现
 * 
 * ### 示例 1: 在 ViewModel 中注册事件处理器
 * ```kotlin
 * class RemoteViewModel : ViewModel() {
 *     init {
 *         // 启动事件循环
 *         ScrcpyEventBus.start()
 *         
 *         // 注册设备断开事件
 *         ScrcpyEventBus.on<ScrcpyEvent.DeviceDisconnected> {
 *             handleDisconnection()
 *         }
 *         
 *         // 注册屏幕尺寸变化事件
 *         ScrcpyEventBus.on<ScrcpyEvent.ScreenInitSize> { event ->
 *             updateScreenSize(event.width, event.height)
 *         }
 *         
 *         // 注册错误事件
 *         ScrcpyEventBus.on<ScrcpyEvent.DemuxerError> { event ->
 *             showError(event.message)
 *         }
 *     }
 *     
 *     override fun onCleared() {
 *         super.onCleared()
 *         // 停止事件循环
 *         ScrcpyEventBus.stop()
 *     }
 * }
 * ```
 * 
 * ### 示例 2: 在解码器中推送事件
 * ```kotlin
 * class VideoDecoder {
 *     private fun handleNewFrame(frameData: ByteArray) {
 *         // 推送新帧事件（对应 iOS 的 SC_EVENT_NEW_FRAME）
 *         ScrcpyEventBus.pushEvent(ScrcpyEvent.NewFrame(frameData))
 *     }
 *     
 *     private fun handleError(error: Exception) {
 *         // 推送错误事件（对应 iOS 的 SC_EVENT_DEMUXER_ERROR）
 *         ScrcpyEventBus.pushEvent(
 *             ScrcpyEvent.DemuxerError(error.message ?: "Unknown error")
 *         )
 *     }
 * }
 * ```
 * 
 * ### 示例 3: 在控制器中推送触摸事件
 * ```kotlin
 * class ScrcpyController {
 *     suspend fun sendTouchEvent(action: Int, x: Int, y: Int) {
 *         // 发送控制消息
 *         sendControlMessage(...)
 *         
 *         // 推送触摸事件到事件系统
 *         when (action) {
 *             0 -> ScrcpyEventBus.pushEvent(
 *                 ScrcpyEvent.MouseButtonDown(x.toFloat(), y.toFloat(), 1)
 *             )
 *             1 -> ScrcpyEventBus.pushEvent(
 *                 ScrcpyEvent.MouseButtonUp(x.toFloat(), y.toFloat(), 1)
 *             )
 *             2 -> ScrcpyEventBus.pushEvent(
 *                 ScrcpyEvent.MouseMotion(x.toFloat(), y.toFloat())
 *             )
 *         }
 *     }
 * }
 * ```
 * 
 * ### 示例 4: 在主线程执行任务（对应 iOS 的 SC_EVENT_RUN_ON_MAIN_THREAD）
 * ```kotlin
 * // 从任意线程推送任务到主线程执行
 * ScrcpyEventBus.postToMainThread {
 *     // 这里的代码会在主线程执行
 *     updateUI()
 *     showToast("操作完成")
 * }
 * ```
 * 
 * ### 示例 5: 退出 Scrcpy（对应 iOS 的 SDL_QUIT）
 * ```kotlin
 * fun stopScrcpy() {
 *     // 推送退出事件
 *     ScrcpyEventBus.pushEvent(ScrcpyEvent.Quit)
 *     
 *     // 事件循环会自动停止
 * }
 * ```
 * 
 * ### 示例 6: 剪贴板同步（对应 iOS 的 SDL_CLIPBOARDUPDATE）
 * ```kotlin
 * fun onResume() {
 *     if (isConnected) {
 *         // 推送剪贴板更新事件
 *         ScrcpyEventBus.pushEvent(ScrcpyEvent.ClipboardUpdate)
 *     }
 * }
 * ```
 * 
 * ## 事件流程对比
 * 
 * ### iOS (SDL 事件系统)
 * ```
 * 解码线程 → SDL_PushEvent(SC_EVENT_NEW_FRAME) → SDL_WaitEvent() → 主线程处理
 * 控制线程 → SDL_PushEvent(SDL_KEYDOWN) → SDL_WaitEvent() → 主线程处理
 * 用户操作 → SDL_PushEvent(SDL_QUIT) → SDL_WaitEvent() → 退出循环
 * ```
 * 
 * ### Android (Kotlin Channel 事件系统)
 * ```
 * 解码线程 → ScrcpyEventBus.pushEvent(NewFrame) → Channel → 事件循环处理
 * 控制线程 → ScrcpyEventBus.pushEvent(KeyDown) → Channel → 事件循环处理
 * 用户操作 → ScrcpyEventBus.pushEvent(Quit) → Channel → 退出循环
 * ```
 * 
 * ## 优势对比
 * 
 * | 特性 | iOS (SDL) | Android (Kotlin Channel) |
 * |------|-----------|--------------------------|
 * | 线程安全 | SDL 内置保证 | Channel 天然线程安全 |
 * | 跨线程通信 | SDL_PushEvent | Channel.send() |
 * | 事件循环 | SDL_WaitEvent | for (event in channel) |
 * | 主线程任务 | SC_EVENT_RUN_ON_MAIN_THREAD | withContext(Dispatchers.Main) |
 * | 平台依赖 | 需要 SDL2 库 | Kotlin 协程标准库 |
 * | 类型安全 | 运行时检查 | 编译时类型检查 |
 * | 协程支持 | 无 | 原生支持 |
 * 
 * ## 注意事项
 * 
 * 1. **事件循环生命周期**：在 ViewModel 或 Service 中管理事件循环的启动和停止
 * 2. **内存泄漏**：确保在组件销毁时停止事件循环
 * 3. **线程安全**：pushEvent 可以从任意线程调用
 * 4. **事件顺序**：事件按推送顺序处理，保证顺序性
 * 5. **错误处理**：事件处理器中的异常会被捕获并记录日志
 */
object ScrcpyEventUsageExample {
    // 此文件仅用于文档说明，不包含可执行代码
}
