import java.util.Properties



plugins {
    alias(libs.plugins.android.application)

    // Add the dependency for the Google services Gradle plugin
    id("com.google.gms.google-services")

}



android {
    namespace = "com.example.abacus_app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.abacus_app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")

        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY") ?: "placeholder"

        // Load local.properties for API keys
        manifestPlaceholders["mapsApiKey"] = mapsApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.google.protobuf") {
            when (requested.name) {
                "protobuf-javalite" -> useVersion("3.23.4")
                "protobuf-lite" -> useVersion("3.0.1") // prevents conflict from espresso-contrib
            }
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    
    // Use compatible versions for SDK 34
    implementation("androidx.activity:activity:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Play Services
    implementation("com.google.android.gms:play-services-base:18.3.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // Navigation - use compatible version
    implementation("androidx.navigation:navigation-fragment:2.7.6")
    implementation("androidx.navigation:navigation-ui:2.7.6")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")

    // Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Security — EncryptedSharedPreferences (OWASP M2)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")

    // ViewModel + LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")

    // QR Scanning
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.2")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // SwipeRefreshLayout - compatible version
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("androidx.viewpager2:viewpager2:1.1.0-beta01")

    // Protobuf used by Firestore
    implementation("com.google.protobuf:protobuf-javalite:3.23.4")

    // Cloudinary for image storage (Spark plan workaround)
    implementation("com.cloudinary:cloudinary-android:3.1.2")


    // Testing
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation(libs.ext.junit)
    testImplementation(libs.runner)
    androidTestImplementation(libs.ext.junit)
    debugImplementation("androidx.fragment:fragment-testing:1.6.2")

    // Mockito for mocking Firebase and other dependencies
    testImplementation("org.mockito:mockito-core:5.8.0")
    androidTestImplementation("org.mockito:mockito-android:5.8.0")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")

    // LiveData testing — makes setValue() run synchronously in tests
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    // Espresso
    androidTestImplementation ("androidx.test.espresso:espresso-contrib:3.5.1") {
        exclude(group = "com.google.protobuf", module = "protobuf-lite")
    }
    androidTestImplementation ("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation ("androidx.test.espresso:espresso-core:3.5.1")
    // AndroidX Test
    androidTestImplementation ("androidx.test:core:1.5.0")
    androidTestImplementation ("androidx.test:runner:1.5.2")
    androidTestImplementation ("androidx.test:rules:1.5.0")

    androidTestImplementation("com.google.protobuf:protobuf-javalite:3.23.4")
}
