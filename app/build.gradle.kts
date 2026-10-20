import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// --- Released package version: one source of truth, independent of DB/backup versions ---------
val versionProperties = Properties().apply {
    val file = rootProject.file("gradle/version.properties")
    require(file.exists()) { "缺少 gradle/version.properties（APK 版本号的唯一来源）" }
    file.inputStream().use { load(it) }
}
val appVersionCode = versionProperties.getProperty("versionCode").toIntOrNull()
    ?: error("gradle/version.properties 缺少有效 versionCode")
val appVersionName = versionProperties.getProperty("versionName")
    ?: error("gradle/version.properties 缺少 versionName")
require(Regex("""^\d+\.\d+\.\d+([-.].+)?$""").matches(appVersionName)) {
    "versionName 必须是 x.y.z 形式：$appVersionName"
}
val publishedVersionsFile = rootProject.file("gradle/published-versions.txt")
if (publishedVersionsFile.exists()) {
    val maxPublished = publishedVersionsFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { it.substringBefore("=").trim().toIntOrNull() }
        .maxOrNull() ?: 0
    require(appVersionCode > maxPublished) {
        "versionCode=$appVersionCode 必须大于已发布最大值 $maxPublished；" +
            "每个发行版都必须单调增长，见 docs/RELEASE_VERSIONING.md"
    }
}

// --- Release signing: material is supplied by the user from OUTSIDE the repository --------------
fun externalValue(name: String): String? =
    (System.getenv(name) ?: providers.gradleProperty(name).orNull)?.trim()?.takeIf { it.isNotEmpty() }

val releaseStorePath = externalValue("POWERCAL_KEYSTORE")
val releaseStorePassword = externalValue("POWERCAL_KEYSTORE_PASSWORD")
val releaseKeyAlias = externalValue("POWERCAL_KEY_ALIAS")
val releaseKeyPassword = externalValue("POWERCAL_KEY_PASSWORD")
val releaseStoreFile = releaseStorePath?.let { file(it) }
val hasReleaseSigning = releaseStoreFile != null &&
    releaseStoreFile.exists() &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "dev.powerfulcalendar"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.powerfulcalendar"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = false
        }
    }

    lint { abortOnError = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation(bom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

/**
 * A stable install must never be signed with the debug key, and this repository must never carry a
 * release keystore or its passwords. Without user-supplied material a release build fails with an
 * explicit instruction instead of silently producing a debug-signed or unsigned "release".
 */
if (!hasReleaseSigning) {
    tasks.register("verifyReleaseSigning") {
        group = "verification"
        description = "Fails release builds until a user-managed release keystore is supplied."
        doLast {
            throw GradleException(
                "缺少长期 Release 签名材料。请由用户自行生成并离线保管 keystore，然后通过环境变量 " +
                    "POWERCAL_KEYSTORE / POWERCAL_KEYSTORE_PASSWORD / POWERCAL_KEY_ALIAS / " +
                    "POWERCAL_KEY_PASSWORD 提供；仓库不会生成、读取或提交密钥。" +
                    "具体步骤见 docs/RELEASE_VERSIONING.md。"
            )
        }
    }
    tasks.matching { it.name == "preReleaseBuild" }.configureEach {
        dependsOn("verifyReleaseSigning")
    }
}

/**
 * Robolectric downloads its android-all runtime on first use. Some build machines only reach the
 * internet through an HTTP proxy; when one is provided via POWERCAL_TEST_PROXY (host:port) it is
 * forwarded to the test JVM. Nothing machine-specific is committed, and a proxy-free machine or CI
 * simply skips this block.
 */
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val proxy = System.getenv("POWERCAL_TEST_PROXY")
    if (!proxy.isNullOrBlank()) {
        val parts = proxy.split(":")
        if (parts.size == 2) {
            systemProperty("http.proxyHost", parts[0])
            systemProperty("http.proxyPort", parts[1])
            systemProperty("https.proxyHost", parts[0])
            systemProperty("https.proxyPort", parts[1])
        }
    }
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
