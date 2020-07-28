package com.tencent.bkrepo.common.service.ribbon

import com.netflix.loadbalancer.AbstractServerPredicate
import com.netflix.loadbalancer.PredicateKey
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import org.springframework.cloud.client.serviceregistry.Registration
import org.springframework.cloud.consul.discovery.ConsulServer

class GrayMetadataAwarePredicate(val properties: RibbonGrayProperties) : AbstractServerPredicate() {
    override fun apply(input: PredicateKey?): Boolean {
        if (input == null) {
            return false
        }
        val registration = SpringContextUtils.getBean(Registration::class.java)
        val localEnvTag = registration.metadata.getOrDefault(ENV, ENV_RELEASE)
        val server = input.server as ConsulServer
        val serverEnvTag = server.metadata.getOrDefault(ENV, ENV_RELEASE)
        return localEnvTag == serverEnvTag
    }

    companion object {
        private const val ENV = "env"
        private const val ENV_RELEASE = "release"
    }
}
