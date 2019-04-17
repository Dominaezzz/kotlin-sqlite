import java.io.ByteArrayOutputStream
import org.gradle.internal.os.OperatingSystem

plugins {
    kotlin("multiplatform") version("1.3.21")
    id("maven-publish")
	id("com.jfrog.bintray") version("1.8.4-jetbrains-3")
}
repositories {
    mavenCentral()
}

val os = OperatingSystem.current()!!

kotlin {
	val isIdeaActive = System.getProperty("idea.active") == "true"

	if (os.isWindows || !isIdeaActive) mingwX64("mingw") {
		val main by compilations.getting {
			defaultSourceSet {
				kotlin.srcDir("src/nativeMain/kotlin")
			}
			cinterops.create("sqlite3") {
				includeDirs("C:/msys64/mingw64/include")
			}
		}
		val test by compilations.getting {
			defaultSourceSet {
				kotlin.srcDir("src/nativeTest/kotlin")
			}
			linkerOpts("-LC:/msys64/mingw64/lib -Wl,-Bstatic -lstdc++ -static")
		}
	}
	if (os.isLinux || !isIdeaActive) linuxX64("linux") {
		val main by compilations.getting {
			defaultSourceSet {
				kotlin.srcDir("src/nativeMain/kotlin")
			}
			cinterops.create("sqlite3") {
				includeDirs("/usr/include", "/usr/include/x86_64-linux-gnu")
			}
		}
		val test by compilations.getting {
			defaultSourceSet {
				kotlin.srcDir("src/nativeTest/kotlin")
			}
			linkerOpts("-L/usr/lib -L/usr/lib/x86_64-linux-gnu")
		}
	}
	if (os.isMacOsX || !isIdeaActive) macosX64("macos") {
		val main by compilations.getting {
			defaultSourceSet {
				kotlin.srcDir("src/nativeMain/kotlin")
			}
			cinterops.create("sqlite3") {
				includeDirs("/usr/local/include", "/opt/local/include")
			}
		}
		val test by compilations.getting {
			defaultSourceSet {
				kotlin.srcDir("src/nativeTest/kotlin")
			}
			linkerOpts("-L/usr/local/lib")
		}

//		iosArm32()
//		iosArm64()
	}
}

kotlin.sourceSets.all {
	languageSettings.apply {
		enableLanguageFeature("InlineClasses")
		useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
	}
}

val stdout = ByteArrayOutputStream()
exec {
	commandLine("git", "describe", "--tags")
	standardOutput = stdout
}

version = stdout.toString().trim()

apply {
	from(rootProject.file("gradle/publish.gradle"))
}
