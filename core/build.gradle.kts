import de.undercouch.gradle.tasks.download.Download
import java.io.ByteArrayOutputStream
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
	kotlin("multiplatform")
	id("de.undercouch.download")
	`maven-publish`
}

val sqliteVersion = "3310100"
val isIdeaActive = System.getProperty("idea.active") == "true"

val sqliteSrcFolder = buildDir.resolve("sqlite_src/sqlite-amalgamation-$sqliteVersion")

val downloadSQLiteSources by tasks.registering(Download::class) {
	src("https://www.sqlite.org/2020/sqlite-amalgamation-$sqliteVersion.zip")
	dest(buildDir.resolve("sqlite_src/sqlite-amalgamation-$sqliteVersion.zip"))
	overwrite(false)
}
val unzipSQLiteSources by tasks.registering(Copy::class) {
	dependsOn(downloadSQLiteSources)

	from(zipTree(downloadSQLiteSources.get().dest))
	into(buildDir.resolve("sqlite_src"))
}

val konanUserDir = file(System.getenv("KONAN_DATA_DIR") ?: "${System.getProperty("user.home")}/.konan")
val konanDeps = konanUserDir.resolve("dependencies")
val toolChainFolderName = when {
	HostManager.hostIsLinux -> "clang-llvm-8.0.0-linux-x86-64"
	HostManager.hostIsMac -> "clang-llvm-apple-8.0.0-darwin-macos"
	HostManager.hostIsMingw -> "msys2-mingw-w64-x86_64-clang-llvm-lld-compiler_rt-8.0.1"
	else -> TODO()
}
val llvmBinFolder = konanDeps.resolve("${toolChainFolderName}/bin")

val compileSQLite by tasks.registering(Exec::class) {
	dependsOn(unzipSQLiteSources)
	environment(
			"PATH" to "$llvmBinFolder;${System.getenv("PATH")}",
			"CPATH" to "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include"
	)

	workingDir = sqliteSrcFolder

	inputs.file(sqliteSrcFolder.resolve("sqlite3.c"))
	outputs.file(sqliteSrcFolder.resolve("sqlite3.o"))

	executable(llvmBinFolder.resolve("clang").absolutePath)
	args("-c", "-Wall")
	args(
			"-DSQLITE_ENABLE_FTS3",
			"-DSQLITE_ENABLE_FTS5",
			"-DSQLITE_ENABLE_RTREE",
			"-DSQLITE_ENABLE_DBSTAT_VTAB",
			"-DSQLITE_ENABLE_JSON1",
			"-DSQLITE_ENABLE_RBU"
	)
	args(
			"-I${sqliteSrcFolder.absolutePath}",
			"-o", "sqlite3.o",
			"sqlite3.c"
	)
}
val archiveSQLite by tasks.registering(Exec::class) {
	dependsOn(compileSQLite)

	workingDir = sqliteSrcFolder

	inputs.file(workingDir.resolve("sqlite3.o"))
	outputs.file(workingDir.resolve("libsqlite3.a"))

	commandLine(
			llvmBinFolder.resolve("llvm-ar").absolutePath,
			"rc", "libsqlite3.a",
			"sqlite3.o"
	)
	environment("PATH", "$llvmBinFolder;${System.getenv("PATH")}")
}

kotlin {
	if (HostManager.hostIsMingw || !isIdeaActive) mingwX64()
	if (HostManager.hostIsLinux || !isIdeaActive) linuxX64()
	if (HostManager.hostIsMac || !isIdeaActive) macosX64()

	targets.withType<KotlinNativeTarget> {
		compilations {
			"main" {
				defaultSourceSet {
					kotlin.srcDir("src/nativeMain/kotlin")
				}
				cinterops.create("sqlite3") {
					includeDirs(sqliteSrcFolder)
					val cInteropTask = tasks[interopProcessingTaskName]
					cInteropTask.dependsOn(unzipSQLiteSources)
					compileSQLite.configure {
						dependsOn(cInteropTask)
					}
				}
				kotlinOptions {
					compileKotlinTask.dependsOn(archiveSQLite)
					freeCompilerArgs = listOf(
							"-include-binary",
							sqliteSrcFolder.resolve("libsqlite3.a").absolutePath
					)
				}
			}
			"test" {
				defaultSourceSet {
					kotlin.srcDir("src/nativeTest/kotlin")
				}
			}
		}
	}
}
