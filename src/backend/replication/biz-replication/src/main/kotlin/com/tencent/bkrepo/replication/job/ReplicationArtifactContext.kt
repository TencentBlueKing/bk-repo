/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.util.okhttp.BasicAuthInterceptor
import com.tencent.bkrepo.common.artifact.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.security.constant.BASIC_AUTH_PREFIX
import com.tencent.bkrepo.replication.api.ClusterReplicaClient
import com.tencent.bkrepo.replication.config.FeignClientFactory
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.ReplicationPackageDetail
import com.tencent.bkrepo.replication.pojo.ReplicationProjectDetail
import com.tencent.bkrepo.replication.pojo.ReplicationRepoDetail
import com.tencent.bkrepo.replication.pojo.setting.RemoteClusterInfo
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.springframework.util.Base64Utils
import java.util.concurrent.TimeUnit

class ReplicationArtifactContext(val task: TReplicaTask, remoteClusterInfo: RemoteClusterInfo) {
    val authToken: String
    val normalizedUrl: String
    val clusterReplicaClient: ClusterReplicaClient
    val httpClient: OkHttpClient
    val currentClusterName: String = remoteClusterInfo.name
    lateinit var projectDetail: ReplicationProjectDetail
    lateinit var currentRepoDetail: ReplicationRepoDetail
    lateinit var currentPackageDetail: ReplicationPackageDetail
    lateinit var extMap: Map<String, Any>

    init {
        with(remoteClusterInfo) {
            authToken = encodeAuthToken(username, password)
            clusterReplicaClient = FeignClientFactory.create(ClusterReplicaClient::class.java, this)
            httpClient = HttpClientBuilderFactory.create(certificate, true).addInterceptor(
                BasicAuthInterceptor(username, password)
            ).connectionPool(ConnectionPool(20, 10, TimeUnit.MINUTES)).build()
            normalizedUrl = normalizeUrl(this)
        }
    }

    companion object {

        fun encodeAuthToken(username: String, password: String): String {
            val byteArray = ("$username${StringPool.COLON}$password").toByteArray(Charsets.UTF_8)
            val encodedValue = Base64Utils.encodeToString(byteArray)
            return "$BASIC_AUTH_PREFIX$encodedValue"
        }

        fun normalizeUrl(remoteClusterInfo: RemoteClusterInfo): String {
            val normalizedUrl = remoteClusterInfo.url
                .trim()
                .trimEnd(StringPool.SLASH[0])
                .removePrefix(StringPool.HTTP)
                .removePrefix(StringPool.HTTPS)
            val prefix = if (remoteClusterInfo.certificate == null) StringPool.HTTP else StringPool.HTTPS
            return "$prefix$normalizedUrl"
        }
    }
}
