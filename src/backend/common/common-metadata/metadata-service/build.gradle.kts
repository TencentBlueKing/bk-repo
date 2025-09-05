/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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
    api(project(":common:common-artifact:artifact-api"))
    api(project(":common:common-metadata:metadata-api"))
    api(project(":common:common-storage:storage-api"))
    api(project(":common:common-security")) {
        exclude(module = "service-servlet")
    }
    api(project(":common:common-service:service-base"))
    api(project(":common:common-stream"))
    api(project(":common:common-query:query-mongo"))
    api(project(":archive:api-archive"))
    api(project(":router-controller:api-router-controller"))
    api(project(":driver:api-driver"))

    compileOnly(project(":common:common-mongo-reactive"))
    compileOnly(project(":common:common-mongo"))
    compileOnly(project(":common:common-service:service-servlet"))
    compileOnly(project(":common:common-service:service-reactive"))

    testImplementation(project(":common:common-service:service-servlet"))
    testImplementation(project(":common:common-mongo-reactive"))
    testImplementation(project(":common:common-mongo"))
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo.spring30x")
    testImplementation("org.mockito.kotlin:mockito-kotlin")
}
