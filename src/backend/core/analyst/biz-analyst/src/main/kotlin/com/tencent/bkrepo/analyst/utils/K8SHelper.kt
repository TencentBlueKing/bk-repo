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

package com.tencent.bkrepo.analyst.utils

import com.tencent.bkrepo.analyst.dispatcher.createClient
import com.tencent.bkrepo.analyst.dispatcher.dsl.V1Secret
import com.tencent.bkrepo.analyst.dispatcher.dsl.metadata
import com.tencent.bkrepo.analyst.pojo.execution.KubernetesExecutionClusterProperties
import com.tencent.bkrepo.common.api.util.jsonCompress
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Secret
import org.slf4j.LoggerFactory
import java.util.Base64

class K8SHelper(k8sProp: KubernetesExecutionClusterProperties) {

    private val client by lazy { createClient(k8sProp) }
    private val coreV1Api by lazy { CoreV1Api(client) }
    private val namespace by lazy { k8sProp.namespace }
    private val logger = LoggerFactory.getLogger(K8SHelper::class.java)
    fun createSecret(
        secretName: String,
        dockerServer: String,
        dockerUserName: String,
        dockerPassword: String,
    ): V1Secret {
        val dockerAuthBytes = "$dockerUserName:$dockerPassword".toByteArray()
        val dockerAuth = Base64.getEncoder().encodeToString(dockerAuthBytes)
        val dockerConfigJson = """
                {
                	"auths": {
                		"$dockerServer": {
                			"username": "$dockerUserName",
                			"password": "$dockerPassword",
                			"auth": "$dockerAuth"
                		}
                	}
                }
        """.trimIndent().jsonCompress()
        val pullSecret = V1Secret {
            apiVersion = "v1"
            kind = "Secret"
            metadata {
                name = secretName
                namespace = this@K8SHelper.namespace
            }
            type = "kubernetes.io/dockerconfigjson"
            data = mapOf(".dockerconfigjson" to dockerConfigJson.encodeToByteArray())
        }
        coreV1Api.createNamespacedSecret(namespace, pullSecret, null, null, null)
        logger.info("Success to create secret[$secretName] on $namespace.")
        return pullSecret
    }

    fun getSecret(secretName: String): V1Secret? {
        return try {
            coreV1Api.readNamespacedSecret(secretName, namespace, null, null, null)
        } catch (e: ApiException) {
            logger.info("Can't get secret[$secretName],cause ${e.message}")
            null
        }
    }
}
