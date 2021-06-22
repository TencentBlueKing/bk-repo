package com.tencent.bkrepo.replication.config.startup

import com.tencent.bkrepo.common.artifact.cluster.ClusterProperties
import com.tencent.bkrepo.common.artifact.cluster.RoleType
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeType
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * 初始化加载node节点配置保存
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ClusterNodeStartLoader(
    private val clusterProperties: ClusterProperties,
    private val clusterNodeService: ClusterNodeService
) : ApplicationRunner {
    override fun run(args: ApplicationArguments?) {
        val userId = SYSTEM_USER
        val request = initClusterNodeCreateRequest()
        try {
            clusterNodeService.create(userId, request)
        } catch (ex: Exception) {
            logger.warn("init cluster node failed, reason: ${ex.message}")
        }
    }

    private fun initClusterNodeCreateRequest(): ClusterNodeCreateRequest {
        return with(clusterProperties) {
            when (role) {
                RoleType.CENTER -> ClusterNodeCreateRequest(
                    name = center.name.orEmpty(),
                    url = center.url.orEmpty(),
                    certificate = center.certificate.orEmpty(),
                    username = center.username.orEmpty(),
                    password = center.password.orEmpty(),
                    type = ClusterNodeType.CENTER
                )
                RoleType.EDGE -> ClusterNodeCreateRequest(
                    name = self.name.orEmpty(),
                    url = self.url.orEmpty(),
                    certificate = self.certificate.orEmpty(),
                    username = self.username.orEmpty(),
                    password = self.password.orEmpty(),
                    type = ClusterNodeType.EDGE
                )
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClusterNodeStartLoader::class.java)
    }
}
