import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.mobile.scrcpy.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mobile.scrcpy.android"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "4.4.1"
        
        // 自动生成 BuildConfig 字段，供代码使用
        buildConfigField("String", "APP_VERSION", "\"v${versionName}\"")
        
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // 启用 CMake 编译 Native 代码
        @Suppress( "UnstableApiUsage")
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_PLATFORM=android-23"
                )
            }
        }
        
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(keystorePropertiesFile.inputStream())
                
                storeFile = rootProject.file(keystoreProperties["storeFile"].toString())
                storePassword = keystoreProperties["storePassword"].toString()
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {
    // Core Android - 最新稳定版本
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    
    // Compose BOM - 2025.01.01 (最新版本)
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.dynamicanimation:dynamicanimation-ktx:1.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    
    // Navigation - 最新稳定版本
    implementation("androidx.navigation:navigation-compose:2.9.6")
    
    // ViewModel - 最新稳定版本
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    
    // Coroutines - 最新稳定版本
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    
    // DataStore - 最新稳定版本
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    
    // JSON 处理 - 最新稳定版本
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    
    // DADB 库 - 纯 Kotlin ADB 实现
    implementation("dev.mobile:dadb:1.2.10")
}
