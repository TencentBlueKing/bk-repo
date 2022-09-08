package com.tencent.bkrepo.replication.replica.base

import com.tencent.bkrepo.common.artifact.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * OkHttpClient池，提供OkHttpClient复用
 * */
object OkHttpClientPool {
    private val clientCache = ConcurrentHashMap<ClusterInfo, OkHttpClient>()
    fun getHttpClient(clusterInfo: ClusterInfo, vararg interceptors: Interceptor): OkHttpClient {
        return clientCache.getOrPut(clusterInfo) {
            val builder = HttpClientBuilderFactory.create(clusterInfo.certificate)
            interceptors.forEach {
                builder.addInterceptor(
                    it
                )
            }
            builder.build()
        }
    }
}
