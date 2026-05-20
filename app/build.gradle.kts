
import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

abstract class SyncDadbHelperAssetTask : Sync() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }
}

// --------------------
// 统一版本属性
// --------------------
fun requireStringProperty(key: String): String =
    project.findProperty(key)?.toString()?.takeIf { it.isNotBlank() }
        ?: error("Missing project property: $key")

val appVersionCode = requireStringProperty("VERSION_CODE")
val appVersionName = requireStringProperty("VERSION_NAME")
val appVersionCodeInt = appVersionCode.toInt()
val appId = "com.screen.remote.android"

val abiCodes =
    mapOf(
        "armeabi-v7a" to 1,
        "arm64-v8a" to 2,
        "x86" to 3,
        "x86_64" to 4,
    )

android {
    namespace = appId
    compileSdk = 36

    defaultConfig {
        applicationId = appId
        minSdk = 23
        targetSdk = 36
        versionCode = appVersionCodeInt
        versionName = appVersionName

        buildConfigField("String", "APP_VERSION", "\"v$appVersionName\"")

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
            proguardFiles(
                this@android.getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    // --------------------
    // Compose 配置
    // --------------------
    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true
    }

    composeCompiler {
        // Release 不需要携带 Compose 源位置信息，避免增加编译/压缩负担。
        includeSourceInformation = false
    }

    // --------------------
    // External Native Build
    // --------------------
    externalNativeBuild {
        cmake {
            path =
                project.layout.projectDirectory
                    .file("src/main/cpp/CMakeLists.txt")
                    .asFile
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

val syncDadbIconHelperAsset by tasks.registering(SyncDadbHelperAssetTask::class) {
    val generatedDir = layout.buildDirectory.dir("generated/assets/dadbHelper")
    dependsOn(gradle.includedBuild("dadb").task(":dadb-helper:dexJar"))
    from(rootProject.file("../external/dadb/dadb-helper/build/libs/dadb-icon-helper.jar"))
    outputDir.set(generatedDir)
    into(generatedDir)
}

// --------------------
// APK 输出文件名
// --------------------
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            syncDadbIconHelperAsset,
            SyncDadbHelperAssetTask::outputDir,
        )

        variant.outputs.forEach { output ->
            val abiName =
                output.filters
                    .find { it.filterType == FilterConfiguration.FilterType.ABI }
                    ?.identifier
                    ?: "universal"
            val abiCode = abiCodes[abiName]

            if (abiCode != null) {
                output.versionCode.set(appVersionCodeInt * 1000 + abiCode)
            }
        }
    }
}

// --------------------
// Dependencies
// --------------------
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
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
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // DADB
    implementation("dev.mobile:dadb:1.2.10")
    implementation("dev.mobile:dadb-android:1.2.10")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.83")

    testImplementation("junit:junit:4.13.2")
}
