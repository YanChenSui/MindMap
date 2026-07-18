import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.mindmap"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.mindmap"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["AMAP_API_KEY"] =
            (localProperties.getProperty("AMAP_API_KEY") ?: "PLEASE_CONFIGURE_AMAP_API_KEY")
        buildConfigField(
            "String",
            "DOUBAO_ASR_API_KEY",
            "\"${localProperties.getProperty("DOUBAO_ASR_API_KEY") ?: ""}\""
        )
        buildConfigField(
            "String",
            "DOUBAO_ASR_APP_KEY",
            "\"${localProperties.getProperty("DOUBAO_ASR_APP_KEY") ?: localProperties.getProperty("DOUBAO_ASR_APP_ID") ?: ""}\""
        )
        buildConfigField(
            "String",
            "DOUBAO_ASR_ACCESS_KEY",
            "\"${localProperties.getProperty("DOUBAO_ASR_ACCESS_KEY") ?: localProperties.getProperty("DOUBAO_ASR_ACCESS_TOKEN") ?: ""}\""
        )
        buildConfigField(
            "String",
            "DOUBAO_ASR_RESOURCE_ID",
            "\"${localProperties.getProperty("DOUBAO_ASR_RESOURCE_ID") ?: "volc.bigasr.auc_turbo"}\""
        )
        buildConfigField(
            "String",
            "DOUBAO_ASR_API_URL",
            "\"${localProperties.getProperty("DOUBAO_ASR_API_URL") ?: "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"}\""
        )
        buildConfigField(
            "String",
            "DOUBAO_LLM_API_KEY",
            "\"${localProperties.getProperty("DOUBAO_LLM_API_KEY") ?: ""}\""
        )
        buildConfigField(
            "String",
            "DOUBAO_LLM_API_URL",
            "\"${localProperties.getProperty("DOUBAO_LLM_API_URL") ?: "https://ark.cn-beijing.volces.com/api/v3/responses"}\""
        )
        buildConfigField(
            "String",
            "DOUBAO_LLM_MODEL",
            "\"${localProperties.getProperty("DOUBAO_LLM_MODEL") ?: ""}\""
        )
        buildConfigField(
            "String",
            "ROS_PROMPT_VERSION",
            "\"${localProperties.getProperty("ROS_PROMPT_VERSION") ?: "ros-v2"}\""
        )
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.fragment)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation("com.amap.api:3dmap-location-search:10.1.200_loc6.4.9_sea9.7.4")
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video)
    implementation(libs.camerax.view)
    implementation(libs.cardview)
    implementation("com.alphacephei:vosk-android:0.3.47")
    testImplementation(libs.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
