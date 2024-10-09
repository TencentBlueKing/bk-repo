/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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
    implementation(project(":fs:api-fs-server"))
    implementation(project(":common:common-stream"))
    implementation(project(":common:common-metrics-push")) {
        exclude(module = "service-servlet")
    }
    implementation(project(":common:common-storage:storage-service"))
    implementation(project(":common:common-mongo-reactive"))
    implementation(project(":common:common-metadata:metadata-service"))
    implementation(project(":common:common-service:service-reactive"))

    implementation("com.playtika.reactivefeign:feign-reactor-spring-cloud-starter")
    implementation("io.micrometer:micrometer-registry-influx")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.google.guava:guava")
    implementation("com.github.ben-manes.caffeine:caffeine:2.9.3")

    testImplementation("org.mockito.kotlin:mockito-kotlin")
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo")
}

configurations.all {
    exclude(module = "devops-plugin-core")
    exclude(group = "com.tencent.devops", module = "devops-web")
}
