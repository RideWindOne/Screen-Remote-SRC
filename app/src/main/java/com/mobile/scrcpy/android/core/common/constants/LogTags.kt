package com.mobile.scrcpy.android.core.common.constants

/**
 * 日志标签常量
 */
object LogTags {
    // 核心组件
    const val SDL = "SDL"
    const val SDL_HM = "SDHM"
    const val APP = "APP"
    const val ADB_MANAGER = "ADBM"
    const val ADB_CONNECTION = "ADBC"
    const val ADB_BRIDGE = "ADBB"
    const val ADB_KEEP_ALIVE_SERVICE = "ADKA"
    const val ADB_PAIRING = "ADBP"
    const val USB_CONNECTION = "USBC"

    // Scrcpy 客户端
    const val SCRCPY_CLIENT = "SCLI"
    const val SCRCPY_SERVICE = "SSVC"
    const val SCRCPY_SERVER = "SSVR"
    const val SCRCPY_BRIDGE = "SBDG"
    const val SCRCPY_EVENT_BUS = "SEVT"

    // 媒体解码
    const val VIDEO_DECODER = "VDEC"
    const val AUDIO_DECODER = "ADEC"
    const val ENCODE = "ENC"
    const val AAC_ENCODE = "AAC"
    const val H264_ENCODE = "H264"
    const val H265_ENCODE = "H265"
    const val OPUS_ENCODE = "OPUS"
    const val CODEC_TEST_SCREEN = "CTST"
    const val AUDIO_CODEC_SELECTOR = "ACSL"
    const val VIDEO_CODEC_SELECTOR = "VCSL"

    // UI 组件
    const val SCREEN_REMOTE_APP = "SAPP"
    const val REMOTE_DISPLAY = "RDSP"
    const val SESSION_DIALOG = "SDLG"
    const val MAIN_SCREEN = "MAIN"
    const val MAIN_VIEW_MODEL = "MVM"

    // ViewModels
    const val SESSION_VM = "SVM"
    const val GROUP_VM = "GVM"
    const val CONNECTION_VM = "CVM"
    const val CONTROL_VM = "CTVM"
    const val ADB_KEYS_VM = "AKVM"
    const val SETTINGS_VM = "STVM"

    // 输入处理
    const val TOUCH_HANDLER = "TOCH"
    const val CONTROL_HANDLER = "CTRL"
    const val CIRCLE_MENU = "CMNU"
    const val FLOATING_CONTROLLER = "FCTL"
    const val FLOATING_CONTROLLER_MSG = "FCTM"

    // 工具类
    const val LOG_MANAGER = "LOG"
    const val TTS_MANAGER = "TTS"
    const val LOGCAT_CAPTURE = "LCAT"
}
