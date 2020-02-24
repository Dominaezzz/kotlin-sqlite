import java.io.ByteArrayOutputStream
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    kotlin("multiplatform") version("1.3.61") apply false
	id("de.undercouch.download") version("4.0.4") apply false
}

val isIdeaActive = System.getProperty("idea.active") == "true"

val stdout = ByteArrayOutputStream()
exec {
	commandLine("git", "describe", "--tags")
	standardOutput = stdout
}

group = "com.github.dominaezzz"
version = stdout.toString().trim()

subprojects {
	group = rootProject.group
	version = rootProject.version

	repositories {
		mavenCentral()
	}

	afterEvaluate {
		configure<KotlinMultiplatformExtension> {
			sourceSets.all {
				languageSettings.apply {
					enableLanguageFeature("InlineClasses")
					useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
				}
			}

			// Hack until https://youtrack.jetbrains.com/issue/KT-30498
			targets.filterIsInstance<KotlinNativeTarget>()
					.filter { it.konanTarget != HostManager.host }
					.forEach { target ->
						configure(target.compilations) {
							configure(cinterops) {
								tasks[interopProcessingTaskName].enabled = false
							}

							compileKotlinTask.enabled = false
						}
						configure(target.binaries) {
							linkTask.enabled = false
						}

						target.mavenPublication(Action {
							val publicationToDisable = this

							tasks.withType<AbstractPublishToMaven> {
								onlyIf {
									publication != publicationToDisable
								}
							}
							tasks.withType<GenerateModuleMetadata> {
								onlyIf {
									publication.get() != publicationToDisable
								}
							}
						})
					}
		}

		configure<PublishingExtension> {
			val vcs: String by project
			val bintrayOrg: String by project
			val bintrayRepository: String by project
			val bintrayPackage: String by project

			repositories {
				maven("https://api.bintray.com/maven/$bintrayOrg/$bintrayRepository/$bintrayPackage/;publish=0;override=0") {
					name = "bintray"
					credentials {
						username = System.getenv("BINTRAY_USER")
						password = System.getenv("BINTRAY_API_KEY")
					}
				}
			}

			publications.withType<MavenPublication> {
				pom {
					name.set(project.name)
					description.set(project.description)
					url.set(vcs)
					licenses {
						license {
							name.set("The Apache Software License, Version 2.0")
							url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
							distribution.set("repo")
						}
					}
					developers {
						developer {
							id.set("Dominaezzz")
							name.set("Dominic Fischer")
						}
					}
					scm {
						connection.set("$vcs.git")
						developerConnection.set("$vcs.git")
						url.set(vcs)
					}
				}
			}
		}

		val publishTasks = tasks.withType<PublishToMavenRepository>()
				.matching {
					when {
						HostManager.hostIsMingw -> it.name.startsWith("publishMingw") || it.name.startsWith("publishJvmMingw")
						HostManager.hostIsMac -> it.name.startsWith("publishMacos") ||
								it.name.startsWith("publishIos") ||
								it.name.startsWith("publishJvmMacos")
						HostManager.hostIsLinux -> it.name.startsWith("publishLinux") ||
								it.name.startsWith("publishJs") ||
								it.name.startsWith("publishJvmPublication") ||
								it.name.startsWith("publishJvmLinux") ||
								it.name.startsWith("publishMetadata") ||
								it.name.startsWith("publishKotlinMultiplatform")
						else -> TODO("Unknown host")
					}
				}
		tasks.register("smartPublish") {
			dependsOn(publishTasks)
		}
	}
}
