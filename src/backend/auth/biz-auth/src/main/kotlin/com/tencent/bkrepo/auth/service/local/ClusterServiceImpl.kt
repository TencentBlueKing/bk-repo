package com.tencent.bkrepo.auth.service.local

import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TCluster
import com.tencent.bkrepo.auth.pojo.AddClusterRequest
import com.tencent.bkrepo.auth.pojo.Cluster
import com.tencent.bkrepo.auth.repository.ClusterRepository
import com.tencent.bkrepo.auth.service.ClusterService
import com.tencent.bkrepo.auth.util.CertTrust
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.Base64

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "local")
class ClusterServiceImpl @Autowired constructor(
    private val clusterRepository: ClusterRepository
) : ClusterService {

    override fun addCluster(request: AddClusterRequest): Boolean {
        val cluster = clusterRepository.findOneByClusterId(request.clusterId)
        if (cluster != null) {
            logger.warn("add cluster [${request.clusterId}]  is exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_DUP_CLUSTERID)
        }

        clusterRepository.insert(
            TCluster(
                clusterId = request.clusterId,
                clusterAddr = request.clusterAddr,
                cert = request.cert,
                credentialStatus = request.credentialStatus
            )
        )
        clusterRepository.findOneByClusterId(request.clusterId) ?: return false
        return true
    }

    override fun ping(clusterId: String): Boolean {
        val cluster = clusterRepository.findOneByClusterId(clusterId)
        if (cluster == null) {
            logger.warn("add cluster [$clusterId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_CLUSTER_NOT_EXIST)
        }
        println(cluster.cert)
        println(String(Base64.getDecoder().decode(cluster.cert)))
        CertTrust.initClient(String(Base64.getDecoder().decode(cluster.cert)))
        val result = CertTrust.call(cluster.clusterAddr)
        println(result)
        return true
    }

    override fun listcluster(): List<Cluster> {
        return clusterRepository.findAllBy().map { transfer(it) }
    }

    private fun transfer(cluster: TCluster): Cluster {
        return Cluster(
            clusterId = cluster.clusterId,
            clusterAddr = cluster.clusterAddr,
            cert = cluster.cert,
            credentialStatus = cluster.credentialStatus
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClusterServiceImpl::class.java)
    }
}
