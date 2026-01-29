import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

// --------------------
// 统一版本属性
// --------------------
val VERSION_CODE: String by project
val VERSION_NAME: String by project
val APP_ID = "com.mobile.scrcpy.android"

val abiCodes =
    mapOf(
        "armeabi-v7a" to 1,
        "arm64-v8a" to 2,
        "x86" to 3,
        "x86_64" to 4,
    )

android {
    namespace = APP_ID
    compileSdk = 36

    defaultConfig {
        applicationId = APP_ID
        minSdk = 23
        targetSdk = 36
        versionCode = VERSION_CODE.toInt()
        versionName = VERSION_NAME

        buildConfigField("String", "APP_VERSION", "\"v$versionName\"")

        vectorDrawables.useSupportLibrary = true

        // ExternalNativeBuild 参数
        // 启用 CMake 编译 Native 代码
        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments +=
                    listOf(
                        "-DANDROID_STL=c++_shared",
                        "-DANDROID_PLATFORM=android-23",
                    )
            }
        }

        ndk {
            // 支持所有主流架构
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    // --------------------
    // ABI splits
    // --------------------
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true // 发布时关闭 universal APK
        }
    }

    // --------------------
    // Signing Config
    // --------------------
    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore.properties")
            if (keystoreFile.exists()) {
                val props = Properties().apply { load(keystoreFile.inputStream()) }
                storeFile = props["storeFile"]?.let { rootProject.file(it.toString()) }
                storePassword = props["storePassword"]?.toString()
                keyAlias = props["keyAlias"]?.toString()
                keyPassword = props["keyPassword"]?.toString()
            }
        }
    }

    // --------------------
    // Build Types
    // --------------------
    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // --------------------
    // Java / Kotlin 兼容性
    // --------------------
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    // --------------------
    // Compose 配置
    // --------------------
    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeCompiler {
        includeSourceInformation = true
    }

    // --------------------
    // External Native Build
    // --------------------
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // --------------------
    // Packaging Options
    // --------------------
    packaging {
        resources.excludes.addAll(
            listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE.md",
                "/META-INF/LICENSE-notice.md",
            ),
        )
    }
}

// --------------------
// ABI versionCode
// --------------------
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abiName =
                output.filters
                    .find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
                    ?.identifier

            // 设置 ABI versionCode
            if (abiName != null) {
                val abiCode = abiCodes[abiName] ?: 0
                output.versionCode.set(VERSION_CODE.toInt() * 1000 + abiCode)
            }
        }
    }
}

// --------------------
// APK 输出文件名
// --------------------
android.applicationVariants.configureEach {
    outputs.configureEach {
        val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        val abiName = output.getFilter("ABI") ?: "universal"
        output.outputFileName = "Screen Remote-$abiName-${VERSION_NAME}_${VERSION_CODE}.apk"
    }
}

// --------------------
// Dependencies
// --------------------
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.3")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2026.01.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.dynamicanimation:dynamicanimation-ktx:1.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation & ViewModel
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // Coroutines & DataStore
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.datastore:datastore-preferences:1.2.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // DADB
    implementation("dev.mobile:dadb:1.2.10")
}
