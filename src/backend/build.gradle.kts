/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "   AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

plugins {
    id("com.tencent.devops.boot") version Versions.DevopsBoot
    id("com.tencent.devops.publish") version Versions.DevopsBoot apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

allprojects {
    group = Release.Group
    version = (System.getProperty("repo_version") ?: Release.Version) +
        if (System.getProperty("snapshot") == "true") "-SNAPSHOT" else "-RELEASE"

    apply(plugin = "com.tencent.devops.boot")
    apply(plugin = "jacoco")

    dependencyManagement {
        applyMavenExclusions(false)

        imports {
            // 升级devops boot版本后，stream启动报错。参考https://github.com/spring-cloud/spring-cloud-function/issues/940
//            mavenBom("org.springframework.cloud:spring-cloud-function-dependencies:${Versions.SpringCloudFunction}")
        }
        dependencies {
            dependency("com.github.zafarkhaja:java-semver:${Versions.JavaSemver}")
            dependency("net.javacrumbs.shedlock:shedlock-spring:${Versions.Shedlock}")
            dependency("net.javacrumbs.shedlock:shedlock-provider-mongo:${Versions.Shedlock}")
            dependency("com.google.code.gson:gson:${Versions.Gson}")
            dependency("org.eclipse.jgit:org.eclipse.jgit.http.server:${Versions.JGit}")
            dependency("org.eclipse.jgit:org.eclipse.jgit:${Versions.JGit}")
            dependency("org.eclipse.jgit:org.eclipse.jgit.junit:${Versions.JGit}")
            dependency("org.apache.commons:commons-compress:${Versions.CommonsCompress}:")
            dependency("commons-io:commons-io:${Versions.CommonsIO}")
            dependency("com.squareup.okhttp3:okhttp:${Versions.OKhttp}")
            dependency("com.google.guava:guava:${Versions.Guava}")
            dependency("com.google.protobuf:protobuf-java-util:${Versions.ProtobufJava}")
            dependency("com.tencent.polaris:polaris-discovery-factory:${Versions.Polaris}")
            dependency("org.apache.commons:commons-text:${Versions.CommonsText}")
            dependency("org.mockito.kotlin:mockito-kotlin:${Versions.MockitoKotlin}")
            dependency("io.mockk:mockk:${Versions.Mockk}")
            dependencySet("io.swagger.core.v3:${Versions.Swagger}") {
                entry("swagger-annotations")
                entry("swagger-models")
            }
            dependency("com.playtika.reactivefeign:feign-reactor-spring-cloud-starter:${Versions.ReactiveFeign}")
            dependency("com.tencent.bk.sdk:crypto-java-sdk:${Versions.CryptoJavaSdk}")
            dependency("org.apache.tika:tika-core:${Versions.TiKa}")
            dependency("com.tencent.bk.sdk:spring-boot-bk-audit-starter:${Versions.Audit}")
            dependency("com.tencent.devops:devops-schedule-common:${Versions.DevopsBootSNAPSHOT}")
            dependency("com.tencent.devops:devops-schedule-model:${Versions.DevopsBootSNAPSHOT}")
            dependency("com.tencent.devops:devops-schedule-server:${Versions.DevopsBootSNAPSHOT}")
            dependency("com.tencent.devops:devops-schedule-model-mongodb:${Versions.DevopsBootSNAPSHOT}")
            dependency("com.tencent.devops:devops-schedule-worker:${Versions.DevopsBootSNAPSHOT}")
            dependency("de.flapdoodle.embed:de.flapdoodle.embed.mongo.spring30x:${Versions.EmbeddedMongo}")
            // pulsar-client中依赖的版本太旧，和otel trace中的版本冲突
            dependency("io.opentelemetry:opentelemetry-api-incubator:1.43.0-alpha")
            // mongodb server版本过低，主动降级驱动
            dependencySet("org.mongodb:5.1.4") {
                entry("bson")
                entry("bson-record-codec")
                entry("mongodb-driver-sync")
                entry("mongodb-driver-core")
                entry("mongodb-driver-reactivestreams")
            }
        }
    }

    configurations.all {
        exclude(group = "log4j", module = "log4j")
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "commons-logging", module = "commons-logging")
        exclude(group = "io.swagger")
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.set(listOf("-java-parameters"))
        }
    }

    tasks.withType<JacocoReport> {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        dependsOn(tasks.getByName("test"))
    }

    tasks.test {
        jvmArgs = listOf("--add-opens=java.base/java.nio=ALL-UNNAMED")
        testLogging {
            events("passed", "skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    if (isBootProject(this)) {
        tasks.named("copyToRelease") {
            dependsOn(tasks.named("bootJar"))
        }
    }
}

nexusPublishing {
    repositories {
        // see https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#configuration
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(System.getenv("SONATYPE_USERNAME"))
            password.set(System.getenv("SONATYPE_PASSWORD"))
        }
    }
}

fun isBootProject(project: Project): Boolean {
    return project.name.startsWith("boot-") || project.findProperty("devops.boot") == "true"
}

apply(from = rootProject.file("gradle/publish-api.gradle.kts"))
apply(from = rootProject.file("gradle/publish-all.gradle.kts"))
