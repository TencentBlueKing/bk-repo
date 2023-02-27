package com.tencent.bkrepo.replication.replica.base

import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.replication.replica.base.interceptor.progress.ProgressInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * OkHttpClient池，提供OkHttpClient复用
 * */
object OkHttpClientPool {
    private val clientCache = ConcurrentHashMap<ClusterInfo, OkHttpClient>()
    fun getHttpClient(clusterInfo: ClusterInfo, readTimeout: Duration, vararg interceptors: Interceptor): OkHttpClient {
        return clientCache.getOrPut(clusterInfo) {
            val builder = HttpClientBuilderFactory.create(clusterInfo.certificate)
                .protocols(listOf(Protocol.HTTP_1_1))
                .readTimeout(readTimeout)
            interceptors.forEach {
                builder.addInterceptor(
                    it
                )
            }
            builder.addNetworkInterceptor(ProgressInterceptor())
            builder.build()
        }
    }
}
