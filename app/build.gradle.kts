import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.android.legacy.kapt)
}

val backendLocalProperties = Properties().apply {
    val propertiesFile = rootProject.file("backend.local.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { load(it) }
    }
}
val localApiBaseUrl = backendLocalProperties.getProperty(
    "apiBaseUrl",
    "https://replace-with-your-ngrok-domain.ngrok-free.app/food-delivery-api/api/"
).trim().let { if (it.endsWith("/")) it else "$it/" }

android {
    namespace = "com.example.dacs_3_composer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.dacs_3_composer"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "backend"
    productFlavors {
        create("alwaysdata") {
            dimension = "backend"
            buildConfigField(
                "String",
                "FOODHUB_API_BASE_URL",
                "\"https://chinh.alwaysdata.net/food-delivery-api/api/\""
            )
        }
        create("local") {
            dimension = "backend"
            buildConfigField("String", "FOODHUB_API_BASE_URL", "\"$localApiBaseUrl\"")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/okio.kotlin_module"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
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
        compose = true
        buildConfig = true
    }
}

// Compile against the versions packaged in each APK, including debug-only Compose dependencies.
// Otherwise UI tooling can raise runtime foundation while source still compiles against an old ABI.
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.components.forEach { component ->
            component.compileConfiguration.shouldResolveConsistentlyWith(component.runtimeConfiguration)
        }
    }
}

dependencies {
    implementation(libs.dagger.hilt.android)
    kapt(libs.dagger.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation("org.maplibre.gl:android-sdk:13.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.firebase:firebase-database-ktx:21.0.0")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.android.gms:play-services-location:21.2.0")

    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("com.cloudinary:cloudinary-android:3.0.2")
    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.ui)

    implementation(libs.firebase.auth)
    implementation(libs.cloudinary.android)
    implementation(libs.coil.compose)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // ✅ Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.browser)

    // ✅ Ktor Server (Dùng Engine CIO ổn định hơn Netty trên Android)
    

    // ✅ PayOS SDK

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
