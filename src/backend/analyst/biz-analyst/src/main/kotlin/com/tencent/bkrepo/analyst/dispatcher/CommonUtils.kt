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

package com.tencent.bkrepo.analyst.dispatcher

import com.tencent.bkrepo.analyst.pojo.execution.KubernetesExecutionClusterProperties
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.util.ClientBuilder
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.credentials.AccessTokenAuthentication
import io.kubernetes.client.util.credentials.ClientCertificateAuthentication
import org.apache.commons.codec.binary.Base64
import java.time.Duration

fun buildCommand(
    cmd: String,
    baseUrl: String,
    subtaskId: String,
    token: String,
    heartbeatTimeout: Duration,
    username: String?,
    password: String?,
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
    username?.let {
        command.add("--username")
        command.add(it)
    }
    password?.let {
        command.add("--password")
        command.add(it)
    }
    return command
}

fun createClient(k8sProps: KubernetesExecutionClusterProperties): ApiClient {
    return if (k8sProps.token != null && k8sProps.apiServer != null) {
        ClientBuilder()
            .setBasePath(k8sProps.apiServer)
            .setAuthentication(AccessTokenAuthentication(k8sProps.token))
            .build()
    } else if (k8sProps.clientKeyData != null && k8sProps.clientCertificateData != null) {
        require(k8sProps.certificateAuthorityData != null)
        require(k8sProps.apiServer != null)
        val cert = Base64.decodeBase64(k8sProps.clientCertificateData)
        val key = Base64.decodeBase64(k8sProps.clientKeyData)
        val ca = Base64.decodeBase64(k8sProps.certificateAuthorityData)
        ClientBuilder()
            .setAuthentication(ClientCertificateAuthentication(cert, key))
            .setCertificateAuthority(ca)
            .setBasePath(k8sProps.apiServer)
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
