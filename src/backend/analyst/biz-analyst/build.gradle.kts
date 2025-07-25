/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

dependencies {
    implementation("com.alibaba:easyexcel:3.1.1")
    implementation(project(":analyst:api-analyst"))
    implementation(project(":analysis-executor:api-analysis-executor"))
    implementation(project(":core:oci:api-oci"))
    implementation(project(":common:common-notify:notify-service"))
    implementation(project(":common:common-service:service-servlet"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation(project(":common:common-redis"))
    implementation(project(":common:common-artifact:artifact-service"))
    implementation(project(":common:common-security"))
    implementation(project(":common:common-mongo"))
    implementation(project(":common:common-query:query-mongo"))
    implementation(project(":common:common-stream"))
    implementation(project(":common:common-lock"))
    implementation(project(":common:common-job"))
    implementation(project(":common:common-statemachine"))
    implementation("io.kubernetes:client-java:${Versions.KubernetesClient}")
    testImplementation("org.mockito.kotlin:mockito-kotlin")
    testImplementation("io.mockk:mockk")
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo")
}
