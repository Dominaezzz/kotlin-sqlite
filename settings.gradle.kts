pluginManagement {
    repositories {
        jcenter()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "http://dl.bintray.com/kotlin/kotlin-eap")
        maven(url = "https://dl.bintray.com/jetbrains/kotlin-native-dependencies")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "kotlin-multiplatform") {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
            if (requested.id.id == "com.jfrog.bintray") {
                useModule("com.jfrog.bintray.gradle:gradle-bintray-plugin:${requested.version}")
            }
        }
    }
}
rootProject.name = "kotlin-sqlite"

enableFeaturePreview("GRADLE_METADATA")
