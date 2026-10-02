import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}
android {
    namespace = "com.hungii.prototype"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.hungii.prototype"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = providers.gradleProperty("hungiiVersionName").getOrElse("0.6-account-sync")
        val config = Properties().apply {
            val source = rootProject.file("local.properties")
            if (source.exists()) source.inputStream().use { load(it) }
        }
        fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        buildConfigField("String", "SUPABASE_URL", quoted(config.getProperty("hungii.supabaseUrl", "")))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", quoted(config.getProperty("hungii.supabasePublishableKey", "")))
        buildConfigField("String", "WORKOS_CLIENT_ID", quoted(config.getProperty("hungii.workosClientId", "")))
        buildConfigField("boolean", "WORKOS_AUTH_READY", (config.getProperty("hungii.workosAuthReady", "false") == "true").toString())
    }
    flavorDimensions += "services"
    productFlavors {
        create("real") {
            dimension = "services"
            buildConfigField("boolean", "LOCAL_DEMO", "false")
            resValue("string", "app_name", "Hungii")
        }
        create("demo") {
            dimension = "services"
            applicationIdSuffix = ".demo"
            buildConfigField("boolean", "LOCAL_DEMO", "true")
            resValue("string", "app_name", "Hungii · Local demo")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    kapt("androidx.room:room-compiler:2.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.browser:browser:1.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
