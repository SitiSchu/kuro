import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.Constants.Constraints

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.17.0"
    id("org.jetbrains.changelog") version "2.2.1"
    id("org.jetbrains.qodana") version "2024.1.5"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").map { it.toInt() }.get())
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) })
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) })
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
}

changelog {
    groups.set(emptyList())
}

intellijPlatform {
    pluginConfiguration {
        name.set(providers.gradleProperty("pluginName"))
        version.set(providers.gradleProperty("pluginVersion"))
        description.set(
            providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"
                if (!it.contains(start) || !it.contains(end)) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                it.substringAfter(start).substringBefore(end).trim()
            }.map { markdownToHTML(it) }
        )
        changeNotes.set(providers.gradleProperty("pluginVersion").map { pluginVersion ->
            changelog.renderItem(
                changelog.run { getOrNull(pluginVersion) ?: getLatest() },
                Changelog.OutputType.HTML
            )
        })
        ideaVersion {
            sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
            untilBuild.set(providers.gradleProperty("pluginUntilBuild"))
        }
    }
    signing {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }
    publishing {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
        channels.set(providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-').substringBefore('.').ifEmpty { "default" }) })
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
    publishPlugin {
        dependsOn("patchChangelog")
    }
}

val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
    task {
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-Drobot-server.port=8082",
                "-Dide.mac.message.dialogs.as.sheets=false",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Djb.consents.confirmation.enabled=false"
            )
        }
    }
    plugins {
        robotServerPlugin(Constraints.LATEST_VERSION)
    }
}
