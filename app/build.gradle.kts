import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.leeauf.pocketnfc"
    compileSdk = 36
    val ciVersionName = providers.gradleProperty("releaseVersionName").orNull
    val ciVersionCode = providers.gradleProperty("releaseVersionCode").orNull?.toInt()

    defaultConfig {
        applicationId = "com.leeauf.pocketnfc"
        minSdk = 29
        targetSdk = 36
        versionCode = ciVersionCode ?: 2_000
        versionName = ciVersionName ?: "0.2.0"
    }

    val releaseStore = providers.gradleProperty("releaseStoreFile").orNull
    val releaseStorePassword = providers.gradleProperty("releaseStorePassword").orNull
    val releaseKeyAlias = providers.gradleProperty("releaseKeyAlias").orNull
    val releaseKeyPassword = providers.gradleProperty("releaseKeyPassword").orNull

    signingConfigs {
        if (listOf(releaseStore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }) {
            create("release") {
                storeFile = file(requireNotNull(releaseStore))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}
