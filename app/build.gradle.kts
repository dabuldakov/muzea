import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android")
    jacoco
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
    // Google Play требует актуальный target API; SDK 36 (Android 16) установлен.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cyber.muzea"
        minSdk = 24
        targetSdk = 36
        versionCode = 103
        versionName = "1.7.36-beta4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Ключ Klipy для поиска гифок. Поиск идёт с клиента напрямую (требование
        // провайдера), поэтому ключ встраивается в сборку. Значение берётся из
        // gradle-свойства KLIPY_APP_KEY или из local.properties (в git не хранится).
        val localProps = Properties().apply {
            rootProject.file("local.properties")
                .takeIf { it.exists() }
                ?.inputStream()?.use { load(it) }
        }
        val klipyAppKey: String = (project.findProperty("KLIPY_APP_KEY") as String?)
            ?: localProps.getProperty("KLIPY_APP_KEY")
            ?: ""
        buildConfigField("String", "KLIPY_APP_KEY", "\"$klipyAppKey\"")
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
            // Покрытие считаем offline-инструментацией (см. ниже): AGP-агент
            // не видит классы, которые Robolectric перезагружает в sandbox.
            enableUnitTestCoverage = false
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
            // R8 временно выключен: в 1.7.19 он сломал generic-тип ответа
            // GitHubApi (ClassCastException в AppUpdateRepository на старте).
            // Включать только после проверки release-сборки на устройстве.
            isMinifyEnabled = true
            isShrinkResources = true
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
        // Нужен BuildConfig.DEBUG: по нему включаем подробный сетевой лог только
        // в отладочной сборке, чтобы в релизе тела запросов/ответов не попадали
        // в logcat (тексты сообщений, токены из ответа логина).
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Нужно Robolectric: тесты кэша работают с реальным Context
            // (cacheDir, externalCacheDirs) и ресурсами приложения.
            isIncludeAndroidResources = true
            // android.util.Log и подобные заглушки не должны ронять тесты,
            // которые проверяют логику, а не Android-рантайм.
            isReturnDefaultValues = true
        }
    }
}

jacoco {
    toolVersion = "0.8.13"
}

// --- Offline-инструментация для unit-тестов ---------------------------------
// Robolectric перезагружает классы приложения в своём sandbox-загрузчике из
// исходных байтов, поэтому on-the-fly агент JaCoCo их не видит. Инструментируем
// классы заранее и подкладываем их в classpath теста.
val jacocoInstrumentedDir = layout.buildDirectory.dir("jacoco/instrumented-debug")

val instrumentDebugUnitTestClasses by tasks.registering {
    // Hilt переписывает байткод (@AndroidEntryPoint) только в этом каталоге:
    // инструментировать kotlin-classes напрямую нельзя, иначе фрагменты
    // теряют Hilt-компонент.
    dependsOn("transformDebugClassesWithAsm")
    val outputDir = jacocoInstrumentedDir
    doLast {
        val target = outputDir.get().asFile
        target.deleteRecursively()
        target.mkdirs()
        val roots = listOf(
            file("build/intermediates/classes/debug/transformDebugClassesWithAsm/dirs")
        ).filter { it.exists() }
        val instrumenter = org.jacoco.core.instr.Instrumenter(
            org.jacoco.core.runtime.OfflineInstrumentationAccessGenerator()
        )
        roots.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .forEach { source ->
                    val copy = File(target, source.relativeTo(root).path)
                    copy.parentFile.mkdirs()
                    source.inputStream().use { input ->
                        copy.outputStream().use { output ->
                            instrumenter.instrument(input, output, source.name)
                        }
                    }
                }
        }
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(instrumentDebugUnitTestClasses)
    // Полный прогон Robolectric держит много sandbox-загрузчиков; дефолтной
    // памяти не хватает и падает OOM на android-all.
    maxHeapSize = "3g"
    // Не включаем on-the-fly агент: классы уже инструментированы offline,
    // иначе они инструментируются дважды.
    extensions.configure<org.gradle.testing.jacoco.plugins.JacocoTaskExtension> {
        isEnabled = false
    }
    val execFile = layout.buildDirectory.file("outputs/unit_test_code_coverage/${name}.exec")
    systemProperty("jacoco-agent.destfile", execFile.get().asFile.absolutePath)
    systemProperty("jacoco-agent.append", "false")
    // Данные покрытия — тоже результат теста: если файл удалили, тест
    // должен выполниться заново, а не взяться из build cache.
    outputs.file(execFile)
    doFirst {
        execFile.get().asFile.delete()
        val original = setOf(
            file("build/intermediates/classes/debug/transformDebugClassesWithAsm/dirs").canonicalPath,
            file("build/tmp/kotlin-classes/debug").canonicalPath,
            file("build/intermediates/javac/debug/compileDebugJavaWithJavac/classes").canonicalPath
        )
        classpath = files(jacocoInstrumentedDir) +
            classpath.filter { it.canonicalPath !in original }
    }
}

tasks.register<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoUnitTestReport") {
    group = "verification"
    description = "Покрытие unit-тестов, включая Robolectric (offline instrumentation)."
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(
            // Тот же каталог, что инструментируется для теста: JaCoCo
            // сопоставляет классы по CRC, иначе отчёт показывает 0%.
            file("build/intermediates/classes/debug/transformDebugClassesWithAsm/dirs")
        ).asFileTree.matching {
            exclude(
                "**/databinding/**",
                "**/Dagger*.class",
                "**/Hilt_*.class",
                "**/*_Factory.class",
                "**/*_HiltModules*",
                "**/*_HiltComponents*",
                "**/*_GeneratedInjector*",
                "**/*_MembersInjector*",
                "**/hilt_aggregated_deps/**",
                // Сгенерированные Glide API-классы — не наш код.
                "**/utils/GlideApp.class",
                "**/utils/GlideOptions.class",
                "**/utils/GlideRequest.class",
                "**/utils/GlideRequests.class",
                "**/GeneratedAppGlideModule*.class"
            )
        }
    )
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        file("build/outputs/unit_test_code_coverage/testDebugUnitTest.exec")
    )
}

dependencies {
    testImplementation("org.jacoco:org.jacoco.agent:0.8.13:runtime")
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

    // DI
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")

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
    // Robolectric — реальный Android-Context (cacheDir, externalCacheDirs) в JVM-тестах.
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation("com.google.dagger:hilt-android-testing:2.48")
    kaptTest("com.google.dagger:hilt-android-compiler:2.48")
    testImplementation("androidx.test:core:1.5.0")
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

kapt {
    // Hilt: не падать на неоднозначных типах, которые kapt видит как error types.
    correctErrorTypes = true
}