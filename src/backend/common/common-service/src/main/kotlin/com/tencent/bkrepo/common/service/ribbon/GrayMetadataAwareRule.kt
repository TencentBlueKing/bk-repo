package com.tencent.bkrepo.common.service.ribbon

import com.netflix.loadbalancer.AbstractServerPredicate
import com.netflix.loadbalancer.AvailabilityPredicate
import com.netflix.loadbalancer.CompositePredicate
import com.netflix.loadbalancer.PredicateBasedRule
import com.netflix.loadbalancer.Server
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import org.springframework.cloud.client.serviceregistry.Registration

class GrayMetadataAwareRule : PredicateBasedRule() {

    private val properties: RibbonGrayProperties = SpringContextUtils.getBean(RibbonGrayProperties::class.java)

    private val predicate: AbstractServerPredicate

    private val localHost: String = SpringContextUtils.getBean(Registration::class.java).host

    init {
        val metadataAwarePredicate = GrayMetadataAwarePredicate(properties)
        val availabilityPredicate = AvailabilityPredicate(this, null)
        predicate = CompositePredicate.withPredicates(metadataAwarePredicate, availabilityPredicate)
            .addFallbackPredicate(AbstractServerPredicate.alwaysTrue())
            .build()
    }

    override fun getPredicate() = predicate

    override fun choose(key: Any?): Server? {
        val serverList = filterServers(loadBalancer.allServers)
        return predicate.chooseRoundRobinAfterFiltering(serverList, key).orNull()
    }

    private fun filterServers(serverList: List<Server>): List<Server> {
        if (!properties.localPrior) {
            return serverList
        }
        for (server in serverList) {
            if (server.host == localHost) {
                return listOf(server)
            }
        }
        return serverList
    }

    private fun createCompositePredicate(vararg predicate: AbstractServerPredicate): CompositePredicate {
        return CompositePredicate.withPredicates(*predicate)
            .addFallbackPredicate(AbstractServerPredicate.alwaysTrue())
            .build()
    }
}
