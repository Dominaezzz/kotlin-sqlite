import de.undercouch.gradle.tasks.download.Download
import org.apache.commons.lang.SystemUtils
import java.io.ByteArrayOutputStream
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform") version("1.3.21")
    id("maven-publish")
	id("com.jfrog.bintray") version("1.8.4-jetbrains-3")
	id("de.undercouch.download") version("3.4.3")
}
repositories {
    mavenCentral()
}

val sqliteVersion = "3270100"

val sqliteSrcFolder = buildDir.resolve("sqlite_src/sqlite-amalgamation-$sqliteVersion")

val downloadSQLiteSources by tasks.registering(Download::class) {
	src("https://www.sqlite.org/2019/sqlite-amalgamation-$sqliteVersion.zip")
	dest(buildDir.resolve("sqlite_src/sqlite-amalgamation-$sqliteVersion.zip"))
	overwrite(false)
}

val unzipSQLiteSources by tasks.registering(Copy::class) {
	dependsOn(downloadSQLiteSources)

	from(zipTree(downloadSQLiteSources.get().dest))
	into(buildDir.resolve("sqlite_src"))
}

val compileSQLite by tasks.registering(Exec::class) {
	dependsOn(unzipSQLiteSources)

	workingDir = sqliteSrcFolder

	executable = "gcc"
	args("-lpthread", "-ldl")

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

val archiveSQLite by tasks.registering(Exec::class) {
	dependsOn(compileSQLite)

	workingDir = sqliteSrcFolder
	executable = "ar"

	args("rcs", "libsqlite3.a", "sqlite3.o")
}

kotlin {
	val isIdeaActive = System.getProperty("idea.active") == "true"

	if (SystemUtils.IS_OS_WINDOWS || !isIdeaActive) mingwX64("mingw")
	if (SystemUtils.IS_OS_LINUX || !isIdeaActive) linuxX64("linux")
	if (SystemUtils.IS_OS_MAC_OSX || !isIdeaActive) macosX64("macos")

	targets.withType<KotlinNativeTarget> {
		compilations["main"].apply {
			defaultSourceSet {
				kotlin.srcDir("src/nativeMain/kotlin")
			}
			cinterops.create("sqlite3") {
				includeDirs(sqliteSrcFolder)

				tasks[interopProcessingTaskName].dependsOn(archiveSQLite)
			}
		}
		compilations["test"].apply {
			defaultSourceSet {
				kotlin.srcDir("src/nativeTest/kotlin")
			}
		}
	}

	sourceSets.all {
		languageSettings.apply {
			enableLanguageFeature("InlineClasses")
			useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
		}
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
