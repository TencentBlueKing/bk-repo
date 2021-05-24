/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.StringPool.COLON
import com.tencent.bkrepo.common.artifact.cluster.FeignClientFactory
import com.tencent.bkrepo.common.artifact.util.okhttp.BasicAuthInterceptor
import com.tencent.bkrepo.common.artifact.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.security.constant.BASIC_AUTH_PREFIX
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.RemoteClusterInfo
import com.tencent.bkrepo.replication.pojo.record.ReplicaProgress
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.`object`.ReplicaObjectInfo
import okhttp3.OkHttpClient
import org.springframework.util.Base64Utils

class ReplicaContext(
    private val taskDetail: ReplicaTaskDetail,
    private val taskObject: ReplicaObjectInfo,
    val clusterNodeInfo: ClusterNodeInfo
) {
    val remoteUrl: String
    val artifactReplicaClient: ArtifactReplicaClient
    val httpClient: OkHttpClient
    val progress: ReplicaProgress = ReplicaProgress()

    private val cluster: RemoteClusterInfo = RemoteClusterInfo(
        name = clusterNodeInfo.name,
        url = clusterNodeInfo.url,
        username = clusterNodeInfo.username,
        password = clusterNodeInfo.password,
        certificate = clusterNodeInfo.certificate
    )

    init {
        remoteUrl = normalizeUrl(cluster)
        artifactReplicaClient = FeignClientFactory.create(cluster)
        httpClient = HttpClientBuilderFactory.create(cluster.certificate, true)
            .addInterceptor(BasicAuthInterceptor(cluster.username.orEmpty(), cluster.password.orEmpty()))
            // .connectionPool(ConnectionPool(20, 10, TimeUnit.MINUTES))
            .build()
    }

    /**
     * 判断任务是否为cron执行任务
     */
    fun isCronJob(): Boolean {
        return taskDetail.task.setting.executionPlan.cronExpression != null
    }

    companion object {
        fun encodeAuthToken(username: String, password: String): String {
            val byteArray = ("$username$COLON$password").toByteArray(Charsets.UTF_8)
            val encodedValue = Base64Utils.encodeToString(byteArray)
            return "$BASIC_AUTH_PREFIX$encodedValue"
        }

        fun normalizeUrl(remoteClusterInfo: RemoteClusterInfo): String {
            val normalizedUrl = remoteClusterInfo.url
                .trim()
                .trimEnd(CharPool.SLASH)
                .removePrefix(StringPool.HTTP)
                .removePrefix(StringPool.HTTPS)
            val prefix = if (remoteClusterInfo.certificate == null) StringPool.HTTP else StringPool.HTTPS
            return "$prefix$normalizedUrl"
        }
    }
}
