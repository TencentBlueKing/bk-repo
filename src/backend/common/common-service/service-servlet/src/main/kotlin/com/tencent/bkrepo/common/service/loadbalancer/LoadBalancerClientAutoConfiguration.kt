package com.tencent.bkrepo.common.service.loadbalancer

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients
import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient
import org.springframework.cloud.loadbalancer.config.BlockingLoadBalancerClientAutoConfiguration
import org.springframework.cloud.loadbalancer.config.LoadBalancerAutoConfiguration
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.client.RestTemplate
import java.net.URI

/**
 * 自定义请求负载均衡客户端，用于解决Open-Feign调用ipv6的报错
 */
@Configuration(proxyBeanMethods = false)
@LoadBalancerClients
@AutoConfigureAfter(LoadBalancerAutoConfiguration::class)
@AutoConfigureBefore(
    org.springframework.cloud.client.loadbalancer.LoadBalancerAutoConfiguration::class,
    BlockingLoadBalancerClientAutoConfiguration::class
)
@ConditionalOnClass(RestTemplate::class)
class LoadBalancerClientAutoConfiguration {
    @Bean
    @ConditionalOnBean(LoadBalancerClientFactory::class)
    @ConditionalOnMissingBean
    @Primary
    fun blockingLoadBalancerClient(
        loadBalancerClientFactory: LoadBalancerClientFactory?,
    ): LoadBalancerClient {
        logger.info("Init LoadBalancerClient.")
        return CustomLoadBalancerClient(loadBalancerClientFactory)
    }

    private class CustomLoadBalancerClient(
        loadBalancerClientFactory: LoadBalancerClientFactory?,
    ) : BlockingLoadBalancerClient(loadBalancerClientFactory) {
        override fun reconstructURI(serviceInstance: ServiceInstance, original: URI): URI {
            return LoadBalancerUriTools.reconstructURI(
                Ipv6CapableDelegatingServiceInstance(serviceInstance), original
            )
        }
    }

    private class Ipv6CapableDelegatingServiceInstance(val delegate: ServiceInstance) : ServiceInstance {
        override fun getServiceId(): String? {
            return delegate.serviceId
        }

        override fun getHost(): String {
            return getAvailableIpv6Host(delegate.host)
        }

        private fun getAvailableIpv6Host(host: String): String {
            return if (host.isNotBlank() && host.contains(":") && !host.startsWith("[")) {
                "[$host]"
            } else host
        }

        override fun getPort(): Int {
            return delegate.port
        }

        override fun isSecure(): Boolean {
            return delegate.isSecure
        }

        override fun getUri(): URI {
            return delegate.uri
        }

        override fun getMetadata(): Map<String, String> {
            return delegate.metadata
        }

        override fun getScheme(): String? {
            return delegate.scheme
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LoadBalancerClientAutoConfiguration::class.java)
    }
}
