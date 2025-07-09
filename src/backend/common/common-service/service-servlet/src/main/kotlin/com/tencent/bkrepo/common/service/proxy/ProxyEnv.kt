/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.service.proxy

import com.tencent.bkrepo.common.api.util.UrlFormatter
import org.springframework.core.io.ClassPathResource
import java.util.Properties

object ProxyEnv {

    private const val PROJECT_ID = "bkrepo.proxy.project.id"
    private const val NAME = "bkrepo.proxy.name"
    private const val SECRET_KEY = "bkrepo.proxy.secret.key"
    private const val CLUSTER_NAME = "bkrepo.proxy.cluster.name"
    private const val GATEWAY = "bkrepo.gateway"
    private const val PROXY_PROPERTIES_FILE_NAME = ".proxy.properties"

    private var projectId: String? = null
    private var name: String? = null
    private var secretKey: String? = null
    private var clusterName: String? = null
    private var gateway: String? = null

    private val properties = Properties()
    private val propertyFileResource = ClassPathResource(PROXY_PROPERTIES_FILE_NAME)
    fun getProjectId(): String {
        if (projectId.isNullOrBlank()) {
            synchronized(this) {
                projectId = getProperty(PROJECT_ID)
            }
        }
        return projectId!!
    }

    fun getName(): String {
        if (name.isNullOrBlank()) {
            synchronized(this) {
                name = getProperty(NAME)
            }
        }
        return name!!
    }

    fun getSecretKey(): String {
        if (secretKey.isNullOrBlank()) {
            synchronized(this) {
                secretKey = getProperty(SECRET_KEY)
            }
        }
        return secretKey!!
    }

    fun getClusterName(): String {
        if (clusterName.isNullOrBlank()) {
            synchronized(this) {
                clusterName = getProperty(CLUSTER_NAME)
            }
        }
        return clusterName!!
    }

    fun getGateway(): String {
        if (gateway.isNullOrBlank()) {
            synchronized(this) {
                gateway = getProperty(GATEWAY)
            }
        }
        return UrlFormatter.formatHost(gateway!!)
    }

    private fun getProperty(key: String): String {
        if (properties.isEmpty) {
            if (!propertyFileResource.exists()) {
                throw RuntimeException("properties is empty and property file resource not exist")
            }

            properties.load(propertyFileResource.inputStream)
        }
        return properties.getProperty(key)
    }
}
