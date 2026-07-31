# Screen Remote Android 开发规则

本文件只适用于 Android 私有源码仓库 `Screen-Remote/`。聚合根仓库使用 `../AGENTS.md`，macOS 仓库使用 `../Screen-Remote-macOS/AGENTS.md`。

## 仓库与文档目标

- Repository target：`XRSec/Screen-Remote-src`
- Workspace path：聚合根目录下的 `Screen-Remote/`
- Project scope：Android application
- Root pointer/release repository：`XRSec/Screen-Remote`
- Android Wiki：`../external/wiki-android/`

Android 实现、测试和工程 skill 属于私有源码仓库。公开根仓库只拥有 Android 项目介绍、发布配置、聚合工具和更新后的子模块指针；不要把根仓库当成 Android 源码 target。

## 实现原则

- 默认不保留旧版本、旧数据结构、旧存储路径或历史行为；除非任务明确要求迁移，否则完整删除过时路径，不添加双读写或兼容层。
- 不因普通修改默认运行完整编译。先运行最窄的针对性检查，再按风险决定是否运行单测、debug build 或设备验证。
- scrcpy channel 必须严格、顺序建立：`video`、可选 `audio`、`control`。server 按 `accept()` 顺序分配角色，禁止并发建链。
- 所有应用日志、调试日志、事件日志、状态摘要及写入日志的异常信息只能使用英文。日志不得通过 `TextPair.get()` 跟随界面语言，不得输出中文或中英双语。

## Compose 与设计系统

修改 Compose UI、主题、颜色、圆角、尺寸、窗口、布局或共享组件前，必须先阅读并遵守 [Android UI 设计系统](../external/wiki-android/开发文档-专题-UI-设计.md)。

- 实现以 token 和共享组件为准，不在业务页面复制颜色、圆角、字号或间距体系。
- 同步检查浅色、深色、紧凑窗口和受影响页面系列。
- 用户可见文字使用模块所属 i18n 对象；日志仍显式使用英文，不复用本地化文案。

## ADB、Android Studio 与设备边界

- 允许使用 ADB 做只读诊断、Logcat、截图、界面层级检查和界面自动化。
- 禁止使用 `adb install`、卸载、清除应用数据或其他 ADB 命令直接安装/替换应用；不得用 ADB 替代 Android Studio 的编译、安装或正常启动流程。
- 需要安装或验证新版本时，必须通过 Android Studio 运行应用。
- MCP 使用保持克制。代码检索、文件读取、编辑、重构和终端命令使用常规本地工具；本项目实际需要的 MCP 仅限 Android Studio 运行应用和查看 Logcat。
- Android Studio MCP 固定运行设备为控制端 `10AEAG2YZS0020P`（vivo V2403A）。运行前先调用 `get_run_configurations` 获取准确配置名，再按需调用 `execute_run_configuration`。
- `build_project` 和 `execute_run_configuration` 只在任务确实需要验证或用户明确要求运行时调用，不因一般代码修改默认触发。
- 需要编译并安装时可使用 Android Studio MCP 的 `execute_run_configuration`。若 MCP 不在线，或运行因非编译问题失败，必须停止并明确提示“Android Studio MCP 不在线”，不得改用 ADB 或其他安装方式绕过。

## 工程 skill 与 pre-push hook

- Android 工程入口位于 `.agents/skills/screen-remote-engineering/`；开始任务时读取其 `SKILL.md` 并按任务类型渐进加载 reference。
- hook 的唯一源码 target 是 `XRSec/Screen-Remote-src`，工作目录是本仓库；它读取 Android outgoing range，并以 `../external/dadb/` 的锁定提交作为依赖证据。
- hook 只允许写入 `../external/wiki-android/`，不得写入 macOS Wiki、Android 源码、dadb 或聚合根仓库。
- 保留实际校验使用的 commit trailer：`Screen-Remote-Review: confirmed`。若以后修改 trailer，必须同步修改 hook 脚本、schema、skill、文档和自动化，禁止只改本文件。
- Android、dadb、Android Wiki 和根 gitlink 必须分别提交；hook 不得把多个仓库 amend 成同一个提交。

## 子代理模型分工

- 对边界清晰、风险较低且可独立完成的编码子任务，优先使用 `gpt-5.3-codex-spark`；模型不可用时才使用当前可用的替代模型，并由主代理复核。
- 适合委派：代码/文件定位、局部实现调查、少量文件机械修改、针对性测试、错误整理和明确范围的独立复查。
- 委派必须写明目标、允许修改范围、相关约束和预期交付物。不会修改同一批文件的任务才并行。
- 主代理负责总体方案、跨模块设计、集成和最终验证。架构决策、模糊需求、复杂并发/生命周期、跨模块重构、Compose UI 验收、Android Studio 运行和设备操作不得未经主代理审查直接采用。
