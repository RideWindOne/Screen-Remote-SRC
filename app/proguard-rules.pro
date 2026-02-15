# ============ Kotlin 相关 ============
-dontwarn kotlin.**
-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ============ JNI / 反射入口 ============
# Native 层通过 FindClass/GetStaticMethodID 定位这些符号，不能混淆或删除。
-keep class com.mobile.scrcpy.android.infrastructure.adb.connection.AdbBridge {
    public static int executeAdbCommand(java.lang.String[]);
    public static int waitProcess(int);
    public static java.lang.String readProcessOutput(int);
    public static boolean terminateProcess(int);
    public static void cleanupProcess(int);
}

-keep class com.mobile.scrcpy.android.core.common.manager.LogManager {
    public static void writeRawLogJNI(java.lang.String, java.lang.String, java.lang.String);
}

# ============ Kotlinx Serialization ============
-keepattributes *Annotation*,InnerClasses,Signature,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-dontnote kotlinx.serialization.AnnotationsKt

-keep @kotlinx.serialization.Serializable class * { *; }
-keep class **$$serializer { *; }
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============ Coroutines ============
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
