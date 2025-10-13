import java.util.Properties

android.buildFeatures.buildConfig = true

val keysPropertiesFile = rootProject.file("keys.properties")
val keysProperties = Properties()
keysProperties.load(keysPropertiesFile.inputStream())

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.hu.sightseek"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hu.sightseek"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val stravaApiKey = keysProperties["STRAVA_API_KEY"]?.toString() ?: ""
        buildConfigField("String", "STRAVA_API_KEY", "\"$stravaApiKey\"")
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
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Map libraries
    implementation(libs.android.maps.utils)
    implementation(libs.osmdroid.android)
    implementation(libs.play.services.location)
    implementation(libs.geofire.android.common)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Other libraries
    implementation(libs.okhttp)
    implementation(libs.glide)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.swiperefreshlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}