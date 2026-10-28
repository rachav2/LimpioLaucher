plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rachav2.civiclauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rachav2.civiclauncher"
        minSdk = 29
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["MAPS_API_KEY"] =
            providers.gradleProperty("MAPS_API_KEY")
                .orElse(providers.environmentVariable("MAPS_API_KEY"))
                .getOrElse("")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Firmado con la llave debug para que el APK se pueda instalar directamente.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}


dependencies {
    implementation("com.google.android.gms:play-services-maps:19.2.0")
}
