import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

// The release workflow reads this literal default and requires a matching vX.Y.Z tag.
val appVersion = providers.gradleProperty("hungiiVersionName").getOrElse("0.9.0")

/** 0.9.0 -> 900, so each published version installs over the previous one. */
fun versionCodeFor(name: String): Int {
    val (major, minor, patch) =
        Regex("""(\d+)\.(\d+)(?:\.(\d+))?""").matchEntire(name)?.destructured
            ?: error("Use an X.Y.Z app version, not $name.")
    val parts = listOf(major, minor, patch.ifEmpty { "0" }).map(String::toInt)
    require(parts[1] < 100 && parts[2] < 100) { "Minor and patch versions must stay below 100." }
    return parts[0] * 10_000 + parts[1] * 100 + parts[2]
}

// One consent version for app and backend; the backend constant is the source of truth.
val privacyVersion =
    rootProject.file("../supabase/functions/_shared/tracker.ts").readText().let { source ->
        Regex("""export const PRIVACY_VERSION = "(\d{4}-\d{2}-\d{2}\.\d+)";""")
            .find(source)
            ?.groupValues
            ?.get(1)
            ?: error("PRIVACY_VERSION was not found in supabase/functions/_shared/tracker.ts.")
    }

android {
    namespace = "com.hungii.prototype"
    compileSdk = 37
    providers.environmentVariable("HUNGII_PREVIEW_KEYSTORE_PATH").orNull?.let { path ->
        require(file(path).isFile) { "Configured preview signing key is missing." }
        signingConfigs.getByName("debug").storeFile = file(path)
    }
    defaultConfig {
        applicationId = "com.hungii.prototype"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeFor(appVersion)
        versionName = appVersion
        val config =
            Properties().apply {
                val source = rootProject.file("local.properties")
                if (source.exists()) source.inputStream().use { load(it) }
            }

        fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        buildConfigField("String", "SUPABASE_URL", quoted(config.getProperty("hungii.supabaseUrl", "")))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", quoted(config.getProperty("hungii.supabasePublishableKey", "")))
        buildConfigField("String", "WORKOS_CLIENT_ID", quoted(config.getProperty("hungii.workosClientId", "")))
        buildConfigField("String", "SIMULATOR_URL", quoted(config.getProperty("hungii.demoApiUrl", "http://10.0.2.2:8788/api")))
        buildConfigField("boolean", "WORKOS_AUTH_READY", (config.getProperty("hungii.workosAuthReady", "false") == "true").toString())
        buildConfigField("String", "PRIVACY_VERSION", quoted(privacyVersion))
    }
    buildTypes {
        release {
            // Previews keep the stable preview certificate so installs can update,
            // but are no longer debuggable and are shrunk with R8.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    flavorDimensions += "services"
    productFlavors {
        create("real") {
            dimension = "services"
            buildConfigField("boolean", "LOCAL_DEMO", "false")
            buildConfigField("String", "DEMO_API_URL", "\"\"")
            resValue("string", "app_name", "Hungii")
        }
        create("demo") {
            dimension = "services"
            applicationIdSuffix = ".demo"
            buildConfigField("boolean", "LOCAL_DEMO", "true")
            buildConfigField("String", "DEMO_API_URL", "BuildConfig.SIMULATOR_URL")
            resValue("string", "app_name", "Hungii · Simulator")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.11.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("androidx.browser:browser:1.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.5.1")
    testImplementation("org.json:json:20260814")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
