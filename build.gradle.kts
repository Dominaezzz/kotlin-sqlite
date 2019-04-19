import de.undercouch.gradle.tasks.download.Download
import java.io.ByteArrayOutputStream
import org.gradle.internal.os.OperatingSystem

plugins {
    kotlin("multiplatform") version("1.3.21")
    id("maven-publish")
	id("com.jfrog.bintray") version("1.8.4-jetbrains-3")
	id("de.undercouch.download") version("3.4.3")
}
repositories {
    mavenCentral()
}

val os = OperatingSystem.current()!!

val sqliteSrcFolder = buildDir.resolve("sqlite_src/sqlite-amalgamation-3270100")

val downloadSQLiteSources by tasks.creating(Download::class) {
	src("https://www.sqlite.org/2019/sqlite-amalgamation-3270100.zip")
	dest(buildDir.resolve("sqlite_src/sqlite-amalgamation-3270100.zip"))
	overwrite(false)
}

val unzipSQLiteSources by tasks.creating(Copy::class) {
	dependsOn(downloadSQLiteSources)

	from(zipTree(downloadSQLiteSources.dest))
	into(buildDir.resolve("sqlite_src"))
}

val buildSQLite by tasks.creating {
	dependsOn(unzipSQLiteSources)

	doLast {
		exec {
			workingDir = sqliteSrcFolder

			executable = "gcc"
			args("-lpthread", "-dl")

			args(
				"-DSQLITE_ENABLE_FTS3",
				"-DSQLITE_ENABLE_FTS5",
				"-DSQLITE_ENABLE_RTREE",
				"-DSQLITE_ENABLE_DBSTAT_VTAB",
				"-DSQLITE_ENABLE_JSON1",
				"-DSQLITE_ENABLE_RBU"
			)

			args("-c", "sqlite3.c", "-o", "sqlite3.o")
		}

		exec {
			workingDir = sqliteSrcFolder
			executable = "ar"

			args("rcs", "libsqlite3.a", "sqlite3.o")
		}
	}
}

kotlin {
	val isIdeaActive = System.getProperty("idea.active") == "true"

	if (os.isWindows || !isIdeaActive) mingwX64("mingw") {
		val main by compilations.getting {
			defaultSourceSet {
				kotlin.srcDir("src/nativeMain/kotlin")
			}
			cinterops.create("sqlite3") {
				includeDirs(sqliteSrcFolder)

				tasks[interopProcessingTaskName].dependsOn(buildSQLite)
			}
		}
		val test by compilations.getting {
			defaultSourceSet {
				kotlin.srcDir("src/nativeTest/kotlin")
			}
		}
	}
	if (os.isLinux || !isIdeaActive) linuxX64("linux") {
		val main by compilations.getting {
			defaultSourceSet {
				kotlin.srcDir("src/nativeMain/kotlin")
			}
			cinterops.create("sqlite3") {
				includeDirs(sqliteSrcFolder)

				tasks[interopProcessingTaskName].dependsOn(buildSQLite)
			}
		}
		val test by compilations.getting {
			defaultSourceSet {
				kotlin.srcDir("src/nativeTest/kotlin")
			}
		}
	}
	if (os.isMacOsX || !isIdeaActive) macosX64("macos") {
		val main by compilations.getting {
			defaultSourceSet {
				kotlin.srcDir("src/nativeMain/kotlin")
			}
			cinterops.create("sqlite3") {
				includeDirs(sqliteSrcFolder)

				tasks[interopProcessingTaskName].dependsOn(buildSQLite)
			}
		}
		val test by compilations.getting {
			defaultSourceSet {
				kotlin.srcDir("src/nativeTest/kotlin")
			}
		}
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
