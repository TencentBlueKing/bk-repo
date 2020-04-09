package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.StringPool.COLON
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.artifact.util.http.BasicAuthInterceptor
import com.tencent.bkrepo.common.artifact.util.http.HttpClientBuilderFactory
import com.tencent.bkrepo.replication.api.ReplicationClient
import com.tencent.bkrepo.replication.config.FeignClientFactory
import com.tencent.bkrepo.replication.model.TReplicationTask
import com.tencent.bkrepo.replication.pojo.ReplicationProjectDetail
import com.tencent.bkrepo.replication.pojo.ReplicationRepoDetail
import com.tencent.bkrepo.replication.pojo.setting.RemoteClusterInfo
import okhttp3.OkHttpClient
import org.springframework.util.Base64Utils

class ReplicationContext(val task: TReplicationTask) {
    val authToken: String
    val normalizedUrl: String
    val replicationClient: ReplicationClient
    val httpClient: OkHttpClient
    lateinit var projectDetailList: List<ReplicationProjectDetail>
    lateinit var currentProjectDetail: ReplicationProjectDetail
    lateinit var currentRepoDetail: ReplicationRepoDetail
    lateinit var remoteProjectId: String
    lateinit var remoteRepoName: String

    init {
        with(task.setting.remoteClusterInfo) {
            authToken = encodeAuthToken(username, password)
            replicationClient = FeignClientFactory.create(ReplicationClient::class.java, this)
            httpClient = HttpClientBuilderFactory.create(certificate).addInterceptor(
                BasicAuthInterceptor(username, password)
            ).build()
            normalizedUrl = normalizeUrl(this)
        }
    }

    companion object {
        fun encodeAuthToken(username: String, password: String): String {
            val byteArray = ("$username$COLON$password").toByteArray(Charsets.UTF_8)
            val encodedValue = Base64Utils.encodeToString(byteArray)
            return "$BASIC_AUTH_HEADER_PREFIX $encodedValue"
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
