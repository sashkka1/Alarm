pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Alarm"

include(":core")
include(":platform")
include(":ui")
include(":app")

// ⚠️ Модуля `:desktop` здесь больше нет: приложение на компьютере уехало в отдельный
// проект Sashboard (ADR-0011). Связь между ними одна — протокол журнала (`LogWire`,
// `LogCodec`, `LogEvent`, `SleepCycleCsv`), и он лежит копией с обеих сторон.
// Правишь протокол здесь — правь и там, иначе телефон отправит то, что компьютер не разберёт.
