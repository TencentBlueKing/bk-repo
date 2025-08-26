package com.tencent.bkrepo.media.job.k8s

import io.kubernetes.client.informer.SharedInformerFactory
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.util.ClientBuilder
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.credentials.AccessTokenAuthentication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
@EnableConfigurationProperties(K8sProperties::class)
class KubernetesConfig @Autowired constructor(
    private val k8sProperties: K8sProperties
) {

    @Bean
    fun apiClient(): ApiClient {
        val client = createClient(k8sProperties)
        client.setVerifyingSsl(false)
        return client
    }

    @Bean
    fun informerClient(): ApiClient {
        val client = createClient(k8sProperties)
        client.setVerifyingSsl(false)
        // Informer 需要长连接，所以不设置超时
        client.setReadTimeout(0)
        return client
    }

    @Bean
    fun sharedInformerFactory(): SharedInformerFactory {
        return SharedInformerFactory()
    }

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