# Add project specific ProGuard rules here.
# -keep class com.mobile.scrcpy.android.** { *; }

# ============ Kotlin 相关 ============
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ============ Jetpack Compose ============
# Compose 运行时需要保留的类
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# ============ 数据模型 ============
# 保留所有数据类（用于序列化/反序列化）
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# 保留 Models.kt 中的数据类
-keep class com.mobile.scrcpy.android.core.data.model.** { *; }

# ============ JNI 相关 ============
# 保留所有 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留 JNI 桥接类
-keep class com.mobile.scrcpy.android.feature.scrcpy.bridge.** { *; }

# ============ ViewModel ============
# 保留 ViewModel 的构造函数（用于反射创建）
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ============ 反射相关 ============
# 保留被反射调用的类和方法
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# ============ 枚举 ============
# 保留枚举类
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============ Parcelable ============
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ============ 序列化 ============
# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============ DataStore ============
# 保留 DataStore Preferences 相关
-keep class androidx.datastore.*.** { *; }

# ============ Coroutines ============
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============ 调试信息 ============
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============ 日志管理 ============
# 保留 LogManager 的所有方法（项目使用自定义日志系统写入文件）
-keep class LogManager {
    public <methods>;
}

# 注意：不移除 android.util.Log 调用，因为：
# 1. LogManager 依赖 Log 输出到 Logcat
# 2. LogManager 同时将日志写入文件
# 3. 项目内部已有日志开关（LogManager.setEnabled()）控制日志收集
# 4. 移除 Log 调用会导致文件日志功能失效
