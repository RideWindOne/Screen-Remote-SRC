import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

abstract class SyncDadbHelperAssetTask : Sync() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }
}

abstract class VerifyScrcpyServerTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val serverFile: RegularFileProperty

    @get:Input
    abstract val serverVersion: Property<String>

    @get:Input
    abstract val expectedSha256: Property<String>

    @TaskAction
    fun verify() {
        val target = serverFile.get().asFile
        check(target.isFile) { "Missing bundled scrcpy-server: ${target.absolutePath}" }
        val actualSha256 =
            target.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        check(actualSha256 == expectedSha256.get()) {
            "Bundled scrcpy-server is not v${serverVersion.get()}: expected=${expectedSha256.get()}" +
                " actual=$actualSha256. Run ./gradlew :app:updateScrcpyServer."
        }
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
val appDisplayVersion = "$appVersionName.$appVersionCode"
val appId = "com.screen.remote.android"
val scrcpyServerVersion = "4.1"
val scrcpyServerSha256 = "deacb991ed2509715160ffdc7907e47b4160eb30d1566217e9047fd5b8850cae"
val telemetryBaseUrl =
    providers.gradleProperty("TELEMETRY_BASE_URL").orElse("").get()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
val scrcpyServerAsset = layout.projectDirectory.file("src/main/assets/scrcpy-server.jar")
val scrcpyServerDownloadUrl =
    "https://github.com/Genymobile/scrcpy/releases/download/v$scrcpyServerVersion/scrcpy-server-v$scrcpyServerVersion"

val defaultAbis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
val targetAbis =
    providers
        .gradleProperty("TARGET_ABI")
        .orElse(defaultAbis.joinToString(","))
        .get()
        .split(',')
        .map(String::trim)
        .filter(String::isNotBlank)

fun sha256(file: File): String =
    file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

val abiCodes =
    mapOf(
        "armeabi-v7a" to 1,
        "arm64-v8a" to 2,
        "x86" to 3,
        "x86_64" to 4,
    )

android {
    namespace = appId
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    ndkVersion = "30.0.15729638"

    defaultConfig {
        applicationId = appId
        minSdk = 23
        targetSdk = 37
        versionCode = appVersionCodeInt
        versionName = appDisplayVersion

        buildConfigField("String", "APP_VERSION", "\"$appDisplayVersion\"")
        buildConfigField("String", "SCRCPY_VERSION", "\"$scrcpyServerVersion\"")
        buildConfigField("String", "SCRCPY_SERVER_SHA256", "\"$scrcpyServerSha256\"")
        buildConfigField("String", "TELEMETRY_BASE_URL", "\"$telemetryBaseUrl\"")

        vectorDrawables.useSupportLibrary = true

        ndk {
            // 支持所有主流架构
            abiFilters += targetAbis
        }
    }

    // --------------------
    // ABI splits
    // --------------------
    splits {
        abi {
            isEnable = true
            reset()
            include(*targetAbis.toTypedArray())
            isUniversalApk = true // 发布时关闭 universal APK
        }
    }

    // --------------------
    // Signing Config
    // --------------------
    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore.properties")
            storeFile = file("./Screen-Remote/release.keystore")
            storePassword = "android"
            keyPassword = "android"
            keyAlias = "Screen-Remote"
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

composeCompiler {
    // Release 不需要携带 Compose 源位置信息，避免增加编译/压缩负担。
    includeSourceInformation = false
}

val syncDadbHelperAsset = tasks.register<SyncDadbHelperAssetTask>("syncDadbHelperAsset") {
    description = "Build and stage the dadb helper JAR as a generated app asset"
    val generatedDir = layout.buildDirectory.dir("generated/assets/dadbHelper")
    dependsOn(gradle.includedBuild("dadb").task(":dadb-helper:dexJar"))
    from(rootProject.file("../external/dadb/dadb-helper/build/libs/dadb-helper.jar"))
    outputDir.set(generatedDir)
    into(generatedDir)
}

tasks.register("updateScrcpyServer") {
    group = "build setup"
    description = "Download and verify scrcpy-server v$scrcpyServerVersion"

    doLast {
        val target = scrcpyServerAsset.asFile
        target.parentFile.mkdirs()
        val temporary = target.resolveSibling("${target.name}.download")
        try {
            URI(scrcpyServerDownloadUrl).toURL().openStream().use { input ->
                temporary.outputStream().use(input::copyTo)
            }
            val actualSha256 = sha256(temporary)
            check(actualSha256 == scrcpyServerSha256) {
                "scrcpy-server v$scrcpyServerVersion SHA256 mismatch: expected=$scrcpyServerSha256 actual=$actualSha256"
            }
            temporary.copyTo(target, overwrite = true)
        } finally {
            temporary.delete()
        }
    }
}

val verifyScrcpyServerVersion = tasks.register<VerifyScrcpyServerTask>("verifyScrcpyServerVersion") {
    group = "verification"
    description = "Verify the bundled scrcpy-server version by its official SHA256"
    serverFile.set(scrcpyServerAsset)
    serverVersion.set(scrcpyServerVersion)
    expectedSha256.set(scrcpyServerSha256)
}

// 每次正常构建都先验证内置 server，避免版本常量、协议代码与 JAR 静默漂移。
tasks.matching { it.name == "preBuild" || it.name == "check" }.configureEach {
    dependsOn(verifyScrcpyServerVersion)
}

// --------------------
// APK 输出文件名
// --------------------
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            syncDadbHelperAsset,
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.dynamicanimation.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Navigation & ViewModel
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Coroutines & DataStore
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // DADB
    implementation(libs.dadb)
    implementation(libs.dadb.android)
    implementation(libs.bouncycastle.bcpkix)

    // QR generation for Wireless Debugging pairing
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
}
