# ============ Kotlin 相关 ============
-dontwarn kotlin.**
-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
-keepclassmembers class **$WhenMappings {
    <fields>;
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
