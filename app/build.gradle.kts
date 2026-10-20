plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.powerfulcalendar"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.powerfulcalendar"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

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
