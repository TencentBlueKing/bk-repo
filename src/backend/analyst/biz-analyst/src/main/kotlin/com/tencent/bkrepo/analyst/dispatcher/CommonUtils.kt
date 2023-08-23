/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.analyst.dispatcher

import com.tencent.bkrepo.analyst.pojo.execution.KubernetesExecutionClusterProperties
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.util.ClientBuilder
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.credentials.AccessTokenAuthentication
import java.time.Duration

fun buildCommand(
    cmd: String, baseUrl: String, subtaskId: String, token: String, heartbeatTimeout: Duration
): List<String> {
    val command = ArrayList<String>()
    command.addAll(cmd.split(" "))
    command.add("--url")
    command.add(baseUrl)
    command.add("--token")
    command.add(token)
    command.add("--task-id")
    command.add(subtaskId)
    command.add("--heartbeat")
    command.add((heartbeatTimeout.seconds / 2L).toString())
    return command
}

fun createClient(k8sProps: KubernetesExecutionClusterProperties): ApiClient {
    return if (k8sProps.token != null && k8sProps.apiServer != null) {
        ClientBuilder()
            .setBasePath(k8sProps.apiServer)
            .setAuthentication(AccessTokenAuthentication(k8sProps.token))
            .build()
    } else {
        // 可通过KUBECONFIG环境变量设置config file路径
        Config.defaultClient()
    }
}

fun ApiException.string(): String {
    return "message: $message\n" +
            "code: $code\n" +
            "headers: $responseHeaders\n" +
            "body: $responseBody"
}
