/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

plugins {
    id("com.tencent.devops.boot") version Versions.DevopsBoot
    id("com.tencent.devops.publish") version Versions.DevopsBoot apply false
}

allprojects {
    group=Release.Group
    version=Release.Version


    repositories {
        // for debug devops-boot locally
        mavenLocal()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        mavenCentral()
        maven(url = "https://repo.spring.io/milestone")
    }

    apply(plugin = "com.tencent.devops.boot")

    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.MINUTES)
    }

    dependencyManagement {
        imports {
            mavenBom("org.springframework.cloud:spring-cloud-sleuth-otel-dependencies:${Versions.SleuthOtel}")
        }
        dependencies {
            val bkrepoVersion = System.getProperty("bkrepo_version") ?: Versions.BkRepo
            dependencySet("com.tencent.bk.repo:$bkrepoVersion") {
                entry("api-auth")
                entry("api-generic")
                entry("common-service")
                entry("common-security")
                entry("artifact-service")
            }
            dependency("org.apache.commons:commons-text:${Versions.CommonsText}")
            dependency("com.tencent.polaris:polaris-discovery-factory:${Versions.Polaris}")
            dependency("com.tencent.bk.sdk:crypto-java-sdk:${Versions.CryptoJavaSdk}")
            dependency("com.squareup.okhttp3:okhttp:${Versions.OKhttp}")
        }
    }
}
