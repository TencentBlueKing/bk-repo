package com.tencent.bkrepo.media.job.k8s

import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.util.ClientBuilder
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.credentials.AccessTokenAuthentication

/**
 * k8s工具类
 * */
object K8sHelper {
    /**
     * 根据k8s属性，创建client，如果没有配置，默认使用本地client
     * */
    fun createClient(k8sProps: K8sProperties): ApiClient {
        return if (k8sProps.token != null && k8sProps.apiServer != null) {
            ClientBuilder()
                .setBasePath(k8sProps.apiServer)
                .setAuthentication(AccessTokenAuthentication(k8sProps.token))
                .build()
        } else {
            Config.defaultClient()
        }
    }
}
