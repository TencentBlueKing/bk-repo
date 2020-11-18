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

rootProject.name = "bk-repo-backend"

pluginManagement {
    val gradlePluginRepoUrl: String by settings
    repositories {
        mavenLocal()
        maven(url = gradlePluginRepoUrl)
        gradlePluginPortal()
        mavenCentral()
        jcenter()
    }
}

include(":boot-assembly")

include(":common")
include(":common:common-api")
include(":common:common-security")
include(":common:common-mongo")
include(":common:common-job")
include(":common:common-service")

include(":common:common-storage")
include(":common:common-storage:storage-api")
include(":common:common-storage:storage-service")

include(":common:common-query")
include(":common:common-query:query-api")
include(":common:common-query:query-mongo")

include(":common:common-artifact")
include(":common:common-artifact:artifact-api")
include(":common:common-artifact:artifact-service")

include(":common:common-notify")
include(":common:common-notify:notify-api")
include(":common:common-notify:notify-service")

include(":repository")
include(":repository:api-repository")
include(":repository:biz-repository")
include(":repository:boot-repository")

include(":auth")
include(":auth:api-auth")
include(":auth:biz-auth")
include(":auth:boot-auth")

include(":opdata")
include(":opdata:api-opdata")
include(":opdata:biz-opdata")
include(":opdata:boot-opdata")

include(":monitor")
include(":monitor:boot-monitor")

include(":replication")
include(":replication:api-replication")
include(":replication:biz-replication")
include(":replication:boot-replication")

include(":generic")
include(":generic:api-generic")
include(":generic:biz-generic")
include(":generic:boot-generic")

include(":docker")
include(":docker:api-docker")
include(":docker:biz-docker")
include(":docker:boot-docker")

include(":maven")
include(":maven:api-maven")
include(":maven:biz-maven")
include(":maven:boot-maven")

include(":npm")
include(":npm:api-npm")
include(":npm:biz-npm")
include(":npm:boot-npm")

include(":pypi")
include(":pypi:api-pypi")
include(":pypi:biz-pypi")
include(":pypi:boot-pypi")

include(":helm")
include(":helm:api-helm")
include(":helm:biz-helm")
include(":helm:boot-helm")

include(":composer")
include(":composer:api-composer")
include(":composer:biz-composer")
include(":composer:boot-composer")

include(":rpm")
include(":rpm:api-rpm")
include(":rpm:biz-rpm")
include(":rpm:boot-rpm")

include(":nuget")
include(":nuget:api-nuget")
include(":nuget:biz-nuget")
include(":nuget:boot-nuget")

include("dockerapi")
include("dockerapi:biz-dockerapi")
include("dockerapi:boot-dockerapi")
