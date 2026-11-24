buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.10.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.56.2")
        // Инструментация классов для offline-JaCoCo: Robolectric грузит байты
        // мимо on-the-fly агента, поэтому считаем покрытие вручную.
        classpath("org.jacoco:org.jacoco.core:0.8.13")
    }
}

plugins {
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}