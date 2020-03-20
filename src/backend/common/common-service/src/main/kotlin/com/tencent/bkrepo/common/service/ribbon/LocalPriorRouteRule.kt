package com.tencent.bkrepo.common.service.ribbon

import com.netflix.loadbalancer.AbstractServerPredicate
import com.netflix.loadbalancer.AvailabilityPredicate
import com.netflix.loadbalancer.CompositePredicate
import com.netflix.loadbalancer.PredicateBasedRule
import com.netflix.loadbalancer.Server
import com.netflix.loadbalancer.ZoneAvoidancePredicate
import com.tencent.bkrepo.common.service.util.NetUtils
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.commons.util.InetUtils
import org.springframework.cloud.consul.discovery.ConsulServer
import java.net.InetAddress

/**
 * 优先调用本机服务
 */
class LocalPriorRouteRule : PredicateBasedRule() {

    private val predicate: AbstractServerPredicate

    private val localHost: String = SpringContextUtils.getBean(InetUtils::class.java).findFirstNonLoopbackHostInfo().ipAddress

    init {

        val zonePredicate = ZoneAvoidancePredicate(this, null)
        val availabilityPredicate = AvailabilityPredicate(this, null)
        predicate = createCompositePredicate(zonePredicate, availabilityPredicate)

    }

    override fun getPredicate() = predicate

    override fun choose(key: Any?): Server? {
        val serverList = filterServers(loadBalancer.allServers)
        return predicate.chooseRoundRobinAfterFiltering(serverList, key).orNull()
    }

    private fun filterServers(serverList: List<Server>): List<Server> {
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
