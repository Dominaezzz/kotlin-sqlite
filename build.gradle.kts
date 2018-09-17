plugins {
	id("org.jetbrains.kotlin.konan").version("0.8.2")
}

konanArtifacts {
	interop("sqlite3") {
		target("linux") {
			compilerOpts("-I/usr/include -I/usr/include/x86_64-linux-gnu")
		}
		target("macos") {
			compilerOpts("-I/usr/local/include -I/opt/local/include")
		}
		target("mingw") {
			compilerOpts("-IC:/msys64/mingw64/include")
		}
	}

	library("ksqlite") {
		srcDir ("src/main/kotlin")
		libraries {
			artifact("sqlite3")
		}
	}

	program("test") {
		srcDir("src/test/kotlin")
		target("linux") {
			linkerOpts("-L/usr/lib -L/usr/lib/x86_64-linux-gnu")
		}
		target("macos") {
			linkerOpts("-L$/usr/local/lib")
		}
		target("mingw") {
			linkerOpts("-LC:/msys64/mingw64/lib -Wl,-Bstatic -lstdc++ -static")
		}
		libraries {
			artifact("ksqlite")
		}
		extraOpts("-tr")
	}
}
