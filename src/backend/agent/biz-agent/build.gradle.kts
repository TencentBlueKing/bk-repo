/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
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
    api(project(":agent:api-agent"))
    api(project(":common:common-artifact:artifact-service"))
    api(project(":common:common-mongo"))
    implementation(project(":common:common-redis"))
    // AgentScope自带okhttp 5的KMP产物，会与仓库统一管理的okhttp 4共存导致重复类，统一使用后者
    api("io.agentscope:agentscope-harness") {
        exclude(group = "com.squareup.okhttp3", module = "okhttp-jvm")
    }
    api("io.agentscope:agentscope-extensions-model-openai") {
        exclude(group = "com.squareup.okhttp3", module = "okhttp-jvm")
    }
    api("io.agentscope:agentscope-extensions-redis") {
        exclude(group = "com.squareup.okhttp3", module = "okhttp-jvm")
    }
    api("io.agentscope:agentscope-extensions-agui") {
        exclude(group = "com.squareup.okhttp3", module = "okhttp-jvm")
    }
}
