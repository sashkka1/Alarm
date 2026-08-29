import com.android.build.api.variant.impl.VariantOutputImpl
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ─────────────────────────────────────────────────────────────────────────────
// Версия: «productVersion.buildNumber», то есть 1.0 → 1.1 → 1.2 → …
//
//   productVersion — версия продукта. ЕДИНСТВЕННОЕ, что правится руками,
//                    при переходе на следующую версию.
//   buildNumber    — считает Gradle. Первая сборка версии продукта получает 0,
//                    дальше +1 на КАЖДУЮ сборочную задачу, включая упавшую:
//                    номер выделяется на фазе конфигурации, до компиляции.
//
// Сменил productVersion — нумерация начинается заново с нуля: 1.7 → 2.0. За этим
// следит служебный ключ countedFor, руками его трогать не нужно.
//
// Отсюда выводятся versionCode, versionName и имя выходного файла.
// Собрать без увеличения номера: .\build.ps1 -NoBump
// ─────────────────────────────────────────────────────────────────────────────

val buildTaskMarkers = listOf("assemble", "bundle", "install")

val versionFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionFile.exists()) versionFile.inputStream().use { load(it) }
}

val productVersion = (versionProps.getProperty("productVersion") ?: "1").trim().toInt()
val countedFor = (versionProps.getProperty("countedFor") ?: productVersion.toString()).trim().toInt()

val bumpRequested = !project.hasProperty("noVersionBump") &&
    gradle.startParameter.taskNames.any { task ->
        buildTaskMarkers.any { task.contains(it, ignoreCase = true) }
    }

// Номер, который получит ИМЕННО ЭТА сборка. Счётчик увеличивается после того,
// как номер выдан, — поэтому самая первая сборка версии продукта получает 0.
val buildNumber = if (countedFor != productVersion) {
    0
} else {
    (versionProps.getProperty("buildNumber") ?: "0").trim().toInt()
}

if (bumpRequested) {
    versionFile.writeText(
        buildString {
            appendLine("# Версия продукта. ЕДИНСТВЕННОЕ, что правится руками — при переходе на следующую версию.")
            appendLine("productVersion=$productVersion")
            appendLine("# Номер сборки внутри этой версии продукта. Считает Gradle, руками не трогать.")
            appendLine("buildNumber=${buildNumber + 1}")
            appendLine("# Служебное: для какой версии продукта посчитан buildNumber. Ставит Gradle.")
            appendLine("countedFor=$productVersion")
        },
    )
    logger.lifecycle("Сборка $productVersion.$buildNumber")
}

android {
    namespace = "com.sasha.alarm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sasha.alarm"
        minSdk = 31
        targetSdk = 36
        // Android требует растущее целое. Тысячи отдаём версии продукта, единицы —
        // номеру сборки: 1.0 → 1000, 1.7 → 1007, 2.0 → 2000. Всегда больше предыдущего.
        versionCode = productVersion * 1000 + buildNumber
        versionName = "$productVersion.$buildNumber"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Личная сборка ставится вручную, поэтому release подписывается
            // отладочным ключом — отдельного keystore пока нет.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    // Модель позы MediaPipe читается через AssetManager.openFd — а он умеет
    // отдавать дескриптор только несжатому файлу. Упакованный zip'ом .task
    // даст отказ загрузки уже на телефоне, а не на сборке.
    androidResources {
        noCompress += "task"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Имя файла: alarm-debug-1.0.apk, дальше alarm-debug-1.1.apk и так далее.
// Версия ВСЕГДА в конце и в том же виде, что versionName внутри приложения.
androidComponents {
    onVariants { variant ->
        val type = variant.buildType ?: variant.name
        variant.outputs.forEach { output ->
            if (output is VariantOutputImpl) {
                output.outputFileName.set("alarm-$type-$productVersion.$buildNumber.apk")
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":platform"))
    implementation(project(":ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
