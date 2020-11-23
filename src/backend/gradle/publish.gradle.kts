apply(plugin = "signing")

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")

configure<PublishingExtension> {
    publications {
        plugins.findPlugin(JavaPlugin::class.java)?.let {
            create<MavenPublication>("jar") {
                from(components["java"])
            }
        }
        plugins.findPlugin(JavaPlatformPlugin::class.java)?.let {
            create<MavenPublication>("pom") {
                from(components["javaPlatform"])
            }
        }

        publications.withType<MavenPublication> {
            pom {
                name.set(project.name)
                description.set(project.description ?: project.name)
                url.set("https://github.com/Tencent/bk-ci")
                licenses {
                    license {
                        name.set("The MIT License (MIT)")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        name.set("bk-ci")
                        email.set("devops@tencent.com")
                        url.set("https://bk.tencent.com")
                        roles.set(listOf("Manager"))
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/Tencent/bk-ci.get")
                    developerConnection.set("scm:git:ssh://github.com/Tencent/bk-ci.git")
                    url.set("https://github.com/Tencent/bk-ci")
                }
            }
        }
    }

    configure<SigningExtension> {
        val signingKey: String? by project
        val signingKeyId: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys( signingKeyId, signingKey, signingPassword)
        setRequired({ isReleaseVersion && gradle.taskGraph.hasTask("upload")})
        sign(publications)
    }
}

extensions.findByType(JavaPluginExtension::class.java)?.run {
    withJavadocJar()
    withSourcesJar()
}

tasks {
    withType<Jar> {
        manifest {
            attributes("Implementation-Title" to (project.description ?: project.name))
            attributes("Implementation-Version" to project.version)
        }
    }
}
