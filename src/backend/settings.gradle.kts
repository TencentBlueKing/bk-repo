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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

rootProject.name = "bk-repo-backend"

pluginManagement {
    repositories {
        if (System.getenv("GITHUB_WORKFLOW") == null) {
            maven(url = "https://mirrors.tencent.com/nexus/repository/gradle-plugins/")
            maven(url = "https://mirrors.tencent.com/nexus/repository/maven-public")
            maven(url = "https://repo.spring.io/milestone")
        } else {
            mavenCentral()
            maven(url = "https://repo.spring.io/milestone")
            gradlePluginPortal()
        }
    }
}

fun File.directories() = listFiles()?.filter { it.isDirectory && it.name != "build" }?.toList() ?: emptyList()

fun includeAll(module: String) {
    include(module)
    val name = module.replace(":", "/")
    file("$rootDir/$name/").directories().forEach {
        include("$module:${it.name}")
    }
}

include(":boot-assembly")
includeAll(":auth")
includeAll(":common")
includeAll(":common:common-storage")
includeAll(":common:common-query")
includeAll(":common:common-artifact")
includeAll(":common:common-notify")
includeAll(":common:common-ratelimiter")
includeAll(":core:generic")
includeAll(":core:composer")
includeAll(":core:helm")
includeAll(":core:maven")
includeAll(":core:npm")
includeAll(":core:pypi")
includeAll(":core:rpm")
includeAll(":core:oci")
includeAll(":core:ddc")
includeAll(":core:nuget")
includeAll(":core:s3")
includeAll(":core:conan")
includeAll(":core:cargo")
includeAll(":core:huggingface")
includeAll(":core:git")
includeAll(":core:lfs")
includeAll(":core:svn")
includeAll(":opdata")
includeAll(":replication")
includeAll(":repository")
includeAll(":webhook")
includeAll(":job")
includeAll(":analyst")
includeAll(":analysis-executor")
includeAll(":common:common-checker")
includeAll(":driver")
includeAll(":config")
includeAll(":archive")
includeAll(":router-controller")
includeAll(":media")
includeAll(":common:common-metadata")
includeAll(":common:common-service")
includeAll(":preview")
includeAll(":websocket")
includeAll(":common:common-archive")
