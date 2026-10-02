import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.gms.google-services")
}

// Ключ подписи release-сборок. Источники в порядке приоритета:
//   1) переменные окружения (их ставит GitHub Actions из secrets репозитория);
//   2) файл keystore.properties в корне проекта (в git не хранится);
//   3) debug-подпись — чтобы сборка не падала, если ключ не настроен.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun releaseSecret(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: keystoreProperties.getProperty(name)?.takeIf { it.isNotBlank() }

val releaseStorePath = releaseSecret("MUZEA_KEYSTORE_PATH")
val releaseSigningReady = releaseStorePath != null &&
    releaseSecret("MUZEA_KEYSTORE_PASSWORD") != null &&
    releaseSecret("MUZEA_KEY_ALIAS") != null &&
    releaseSecret("MUZEA_KEY_PASSWORD") != null

android {
    namespace = "com.example.muzea"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cyber.muzea"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseSecret("MUZEA_KEYSTORE_PASSWORD")
                keyAlias = releaseSecret("MUZEA_KEY_ALIAS")
                keyPassword = releaseSecret("MUZEA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Тот же ключ, что и у релиза. Иначе сборка из Android Studio не
            // встанет поверх установленной релизной (INSTALL_FAILED_UPDATE_
            // INCOMPATIBLE), а релизная — поверх отладочной. Флаг DEBUGGABLE
            // при этом сохраняется, так что в Google Play и RuStore такая
            // сборка всё равно не пройдёт.
            signingConfig = if (releaseSigningReady) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        release {
            signingConfig = if (releaseSigningReady) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "MUZEA: ключ подписи не настроен, release-сборка будет подписана " +
                        "отладочным ключом. Установить настоящий ключ можно через " +
                        "keystore.properties или переменные MUZEA_KEYSTORE_*."
                )
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Glide with OkHttp integration (для авторизации)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // Добавьте новые Media3 зависимости:
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.3.1")

    // Permissions
    implementation("com.karumi:dexter:6.2.3")

    // Шифрование учётных данных в локальном хранилище (Android Keystore)
    implementation("androidx.security:security-crypto:1.1.0-beta01")

    // Swipe to refresh
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // push google (BOM 32.7.4 совместим с Kotlin 1.9.20; 34.x требует Kotlin 2.x)
    // firebase-analytics намеренно не подключается: аналитика не используется,
    // а SDK автоматически собирает и передаёт данные за пределы РФ.
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-messaging")
}

// Исключаем Compose зависимости
configurations.all {
    exclude(group = "androidx.compose")
    exclude(group = "androidx.activity", module = "activity-compose")
}