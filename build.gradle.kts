import org.gradle.api.publish.PublishingExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

// Wires the GitHub Packages Maven repository into any subproject that
// applies `maven-publish`. The CI release workflow runs `:publishLibraries`,
// which fans out to each library module's
// `publishAllPublicationsToGitHubPackagesRepository`. No library module
// exists today — when one is added (apply `maven-publish` + declare a
// publication), it will publish on the next tag without further wiring.
val githubRepository = providers.gradleProperty("github.repository")
    .orElse(providers.environmentVariable("GITHUB_REPOSITORY"))
    .orElse("yschimke/shokz")

val publishLibraries = tasks.register("publishLibraries") {
    group = "publishing"
    description = "Publishes all library publications to GitHub Packages " +
        "(no-op until a module applies the maven-publish plugin)."
}

subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/${githubRepository.get()}")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                            ?: providers.gradleProperty("gpr.user").orNull
                        password = System.getenv("GITHUB_TOKEN")
                            ?: providers.gradleProperty("gpr.token").orNull
                    }
                }
            }
        }
        publishLibraries.configure {
            dependsOn(tasks.named("publishAllPublicationsToGitHubPackagesRepository"))
        }
    }
}
