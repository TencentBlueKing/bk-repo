/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import de.marcphilipp.gradle.nexus.NexusPublishExtension
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    kotlin("jvm") version "1.3.72"
    kotlin("plugin.spring") version "1.3.72" apply false
    id("org.springframework.boot") version "2.3.2.RELEASE" apply false
    id("io.spring.dependency-management") version "1.0.9.RELEASE"
    id("de.marcphilipp.nexus-publish") version "0.4.0" apply false
    id("io.codearte.nexus-staging") version "0.22.0"
}

allprojects {
    group = "com.tencent.bkrepo"
    version = "0.8.30"

    repositories {
        val publicMavenRepoUrl: String by project
        val privateMavenRepoUrl: String by project

        mavenLocal()
        maven(url = publicMavenRepoUrl)
        maven(url = privateMavenRepoUrl)
        maven(url = "https://repo.spring.io/libs-milestone")
        mavenCentral()
        jcenter()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(from = rootProject.file("gradle/ktlint.gradle.kts"))

    dependencyManagement {
        imports {
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:Hoxton.SR7")
        }
        dependencies {
            dependency("com.github.zafarkhaja:java-semver:0.9.0")
            dependency("io.swagger:swagger-annotations:1.5.22")
            dependency("io.swagger:swagger-models:1.5.22")
            dependency("io.springfox:springfox-swagger2:2.9.2")
            dependency("com.amazonaws:aws-java-sdk-s3:1.11.700")
            dependency("com.tencent:innercos-java-sdk:5.6.7")
            dependency("com.google.guava:guava:29.0-jre")
            dependency("org.apache.commons:commons-compress:1.18")
            dependency("org.apache.hadoop:hadoop-hdfs:2.6.0")
            dependency("org.apache.hadoop:hadoop-common:2.6.0")
            dependency("commons-io:commons-io:2.6")
            dependency("org.apache.skywalking:apm-toolkit-logback-1.x:6.6.0")
            dependency("org.apache.skywalking:apm-toolkit-trace:6.6.0")
            dependency("net.javacrumbs.shedlock:shedlock-spring:4.12.0")
            dependency("net.javacrumbs.shedlock:shedlock-provider-mongo:4.12.0")
            dependency("io.jsonwebtoken:jjwt-api:0.11.2")
            dependency("io.jsonwebtoken:jjwt-impl:0.11.2")
            dependency("io.jsonwebtoken:jjwt-jackson:0.11.2")
            // fix issue https://github.com/spring-projects/spring-boot/issues/16407
            // https://issues.redhat.com/browse/UNDERTOW-1743
            dependency("io.undertow:undertow-core:2.1.1.Final")
            dependency("io.undertow:undertow-servlet:2.1.1.Final")
            dependency("io.undertow:undertow-websockets-jar:2.1.1.Final")
        }
    }

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
        implementation(kotlin("reflect"))
        testImplementation("org.springframework.boot:spring-boot-starter-test") {
            exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        }
    }

    configurations.all {
        exclude(group = "log4j", module = "log4j")
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "commons-logging", module = "commons-logging")
    }

    tasks {
        compileKotlin {
            kotlinOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
            kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
        compileTestKotlin {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
        test {
            useJUnitPlatform()
        }
    }

    val jar: Jar by tasks
    val bootJar: BootJar by tasks
    val bootRun: BootRun by tasks

    val isBootProject = project.name.startsWith("boot-")

    bootJar.enabled = isBootProject
    bootRun.enabled = isBootProject
    jar.enabled = !isBootProject
}

nexusStaging {
    username = System.getenv("SONATYPE_USERNAME")
    password = System.getenv("SONATYPE_PASSWORD")
}

val publishList = listOf(
    ":common:common-api",
    ":common:common-artifact:artifact-api",
    ":common:common-query:query-api",
    ":generic:api-generic",
    ":repository:api-repository"
)

publishList.forEach {
    project(it) {
        apply(plugin = "de.marcphilipp.nexus-publish")
        apply(from = rootProject.file("gradle/publish.gradle.kts"))
        configure<NexusPublishExtension> {
            repositories {
                sonatype()
            }
        }
    }
}
