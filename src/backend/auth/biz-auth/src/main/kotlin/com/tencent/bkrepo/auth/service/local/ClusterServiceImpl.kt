package com.tencent.bkrepo.auth.service.local

import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TCluster
import com.tencent.bkrepo.auth.pojo.AddClusterRequest
import com.tencent.bkrepo.auth.pojo.Cluster
import com.tencent.bkrepo.auth.pojo.UpdateClusterRequest
import com.tencent.bkrepo.auth.repository.ClusterRepository
import com.tencent.bkrepo.auth.service.ClusterService
import com.tencent.bkrepo.auth.util.CertTrust
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "local")
class ClusterServiceImpl @Autowired constructor(
    private val clusterRepository: ClusterRepository,
    private val mongoTemplate: MongoTemplate
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
        try {
            val cluster = clusterRepository.findOneByClusterId(clusterId)
            if (cluster == null) {
                logger.warn("ping cluster [$clusterId]  not exist.")
                setClusterCredentialStatus(clusterId, false)
                return false
            }
            CertTrust.initClient(cluster.cert)
            var addr = cluster.clusterAddr.removeSuffix("/") + "/cluster/credential"
            CertTrust.call(addr)
            setClusterCredentialStatus(clusterId, true)
            return true
        } catch (e: Exception) {
            logger.warn("ping cluster [$clusterId]  failed.")
            setClusterCredentialStatus(clusterId, false)
            return false
        }
    }

    override fun delete(clusterId: String): Boolean {
        val result = clusterRepository.deleteByClusterId(clusterId)
        if (result == 0L) {
            logger.warn("delete cluster [$clusterId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_CLUSTER_NOT_EXIST)
        }
        return true
    }

    override fun updateCluster(clusterId: String, request: UpdateClusterRequest): Boolean {
        val cluster = clusterRepository.findOneByClusterId(clusterId)
        if (cluster == null) {
            logger.warn("update cluster [$clusterId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_CLUSTER_NOT_EXIST)
        }

        val query = Query.query(Criteria.where(TCluster::clusterId.name).`is`(clusterId))
        val update = Update()

        if (request.credentialStatus != null) {
            update.set(TCluster::credentialStatus.name, request.credentialStatus!!)
        }

        if (request.cert != "") {
            update.set(TCluster::cert.name, request.cert)
        }

        if (request.clusterAddr != "") {
            update.set(TCluster::clusterAddr.name, request.clusterAddr)
        }

        val result = mongoTemplate.upsert(query, update, TCluster::class.java)
        if (result.matchedCount == 1L) {
            return true
        }
        return false
    }

    override fun listCluster(): List<Cluster> {
        return clusterRepository.findAllBy().map { transfer(it) }
    }

    private fun setClusterCredentialStatus(clusterId: String, status: Boolean): Boolean {
        val query = Query()
        query.addCriteria(Criteria.where(TCluster::clusterId.name).`is`(clusterId))
        val update = Update()
        update.set("credentialStatus", status)
        val result = mongoTemplate.updateFirst(query, update, TCluster::class.java)
        if (result.modifiedCount == 1L) {
            return true
        }
        return false
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
