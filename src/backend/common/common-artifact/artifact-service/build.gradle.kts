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

dependencies {
    api(project(":repository:api-repository"))
    api(project(":auth:api-auth"))
    api(project(":replication:api-replication"))
    api(project(":driver:api-driver"))
    api(project(":router-controller:api-router-controller"))
    api(project(":archive:api-archive"))
    api(project(":common:common-service:service-servlet"))
    api(project(":common:common-security"))
    api(project(":common:common-artifact:artifact-api"))
    api(project(":common:common-storage:storage-service"))
    api(project(":common:common-ratelimiter"))
    api(project(":common:common-stream"))
    api(project(":common:common-metrics-push"))
    api(project(":common:common-metadata:metadata-service"))
    api(project(":common:common-mongo"))

    api("org.springframework.boot:spring-boot-starter-aop")
    api("io.micrometer:micrometer-registry-prometheus")
    api("org.influxdb:influxdb-java")
    api("org.apache.commons:commons-text")
    api("com.tencent.bk.sdk:spring-boot-bk-audit-starter")

    testImplementation("org.mockito.kotlin:mockito-kotlin")
    testImplementation("io.mockk:mockk")
}
