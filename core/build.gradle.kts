import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.presetName

plugins {
	kotlin("multiplatform")
	id("de.undercouch.download")
	`maven-publish`
}

val sqliteVersion = "3310100"
val useSingleTarget: Boolean by rootProject.extra

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
	else -> error("Unknown host OS")
}
val llvmBinFolder = konanDeps.resolve("${toolChainFolderName}/bin")

val androidSysRootParent = konanDeps.resolve("target-sysroot-1-android_ndk").resolve("android-21")

data class TargetInfo(val targetName: String, val sysRoot: File, val clangArgs: List<String> = emptyList())
val targetInfoMap = mapOf(
		KonanTarget.LINUX_X64 to TargetInfo(
				"x86_64-unknown-linux-gnu",
				konanDeps.resolve("target-gcc-toolchain-3-linux-x86-64/x86_64-unknown-linux-gnu/sysroot")
		),
		KonanTarget.MACOS_X64 to TargetInfo(
				"x86_64-apple-darwin10", // Not sure about this but it doesn't matter yet.
				konanDeps.resolve("target-sysroot-10-macos_x64")
		),
		KonanTarget.MINGW_X64 to TargetInfo(
				"x86_64-w64-mingw32",
				konanDeps.resolve("msys2-mingw-w64-x86_64-clang-llvm-lld-compiler_rt-8.0.1")
		),
		KonanTarget.MINGW_X86 to TargetInfo(
				"i686-w64-mingw32",
				konanDeps.resolve("msys2-mingw-w64-i686-clang-llvm-lld-compiler_rt-8.0.1")
		),
		KonanTarget.LINUX_ARM32_HFP to TargetInfo(
				"armv6-unknown-linux-gnueabihf",
				konanDeps.resolve("target-sysroot-2-raspberrypi"),
				listOf("-mfpu=vfp", "-mfloat-abi=hard")
		),
		KonanTarget.ANDROID_ARM32 to TargetInfo(
				"arm-linux-androideabi",
				androidSysRootParent.resolve("arch-arm")
		),
		KonanTarget.ANDROID_ARM64 to TargetInfo(
				"aarch64-linux-android",
				androidSysRootParent.resolve("arch-arm64")
		),
		KonanTarget.ANDROID_X86 to TargetInfo(
				"i686-linux-android",
				androidSysRootParent.resolve("arch-x86")
		),
		KonanTarget.ANDROID_X64 to TargetInfo(
				"x86_64-linux-android",
				androidSysRootParent.resolve("arch-x64")
		)
)

kotlin {
	if (HostManager.hostIsMingw || !useSingleTarget) mingwX64()
	if (HostManager.hostIsLinux || !useSingleTarget) linuxX64()
	if (HostManager.hostIsMac || !useSingleTarget) macosX64()
	if (!useSingleTarget) {
		mingwX86()
		linuxArm32Hfp()
	}

	targets.withType<KotlinNativeTarget> {
		val targetDir = sqliteSrcFolder.resolve(konanTarget.presetName)

		val sourceFile = sqliteSrcFolder.resolve("sqlite3.c")
		val objFile = targetDir.resolve("sqlite3.o")
		val staticLibFile = targetDir.resolve("libsqlite3.a")

		val compileSQLite = tasks.register<Exec>("compileSQLite${konanTarget.presetName.capitalize()}") {
			onlyIf { HostManager().isEnabled(konanTarget) }

			dependsOn(unzipSQLiteSources)
			environment("PATH", "$llvmBinFolder;${System.getenv("PATH")}")
			if (HostManager.hostIsMac && konanTarget == KonanTarget.MACOS_X64) {
				environment("CPATH", "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include")
			}

			inputs.file(sourceFile)
			outputs.file(objFile)

			executable(llvmBinFolder.resolve("clang").absolutePath)
			args("-c", "-Wall")

			val targetInfo = targetInfoMap.getValue(konanTarget)

			args("--target=${targetInfo.targetName}", "--sysroot=${targetInfo.sysRoot}")
			args(targetInfo.clangArgs)
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
					"-o", objFile.absolutePath,
					sourceFile.absolutePath
			)
		}
		val archiveSQLite = tasks.register<Exec>("archiveSQLite${konanTarget.presetName.capitalize()}") {
			onlyIf { HostManager().isEnabled(konanTarget) }
			dependsOn(compileSQLite)

			inputs.file(objFile)
			outputs.file(staticLibFile)

			executable(llvmBinFolder.resolve("llvm-ar").absolutePath)
			args(
					"rc", staticLibFile.absolutePath,
					objFile.absolutePath
			)
			environment("PATH", "$llvmBinFolder;${System.getenv("PATH")}")
		}

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
					freeCompilerArgs = listOf("-include-binary", staticLibFile.absolutePath)
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
