package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool.UNKNOWN
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.replication.api.ClusterReplicaClient
import com.tencent.bkrepo.replication.config.FeignClientFactory
import com.tencent.bkrepo.replication.dao.ClusterNodeDao
import com.tencent.bkrepo.replication.message.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TClusterNode
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeType
import com.tencent.bkrepo.replication.pojo.cluster.RemoteClusterInfo
import com.tencent.bkrepo.replication.repository.ClusterNodeRepository
import com.tencent.bkrepo.replication.service.ClusterNodeService
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Service
class ClusterNodeServiceImpl(
    private val clusterNodeDao: ClusterNodeDao,
    private val clusterNodeRepository: ClusterNodeRepository
) : ClusterNodeService {

    override fun getByClusterId(id: String): ClusterNodeInfo? {
        return convert(clusterNodeRepository.findByIdOrNull(id))
    }

    override fun getByClusterName(name: String): ClusterNodeInfo? {
        return convert(clusterNodeDao.findByName(name))
    }

    override fun getCenterNode(): ClusterNodeInfo {
        val clusterInfoList = clusterNodeDao.listByNameAndType(type = ClusterNodeType.CENTER)
        require(clusterInfoList.size == 1) { "find no or more than one master cluster." }
        return clusterInfoList.map { convert(it)!! }.first()
    }

    override fun listEdgeNodes(): List<ClusterNodeInfo> {
        return clusterNodeDao.listByNameAndType(type = ClusterNodeType.EDGE).map { convert(it)!! }
    }

    override fun listClusterNodes(name: String?, type: ClusterNodeType?): List<ClusterNodeInfo> {
        return clusterNodeDao.listByNameAndType(name, type).map { convert(it)!! }
    }

    override fun existClusterName(name: String): Boolean {
        return clusterNodeDao.findByName(name) != null
    }

    override fun create(userId: String, request: ClusterNodeCreateRequest): ClusterNodeInfo {
        with(request) {
            validateParameter(this)
            if (existClusterName(name)) {
                throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_EXISTS, name)
            }
            // 中心节点唯一
            if (type == ClusterNodeType.CENTER && checkCenterNodeExist(type)) {
                throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_EXISTS, name)
            }
            val clusterNode = TClusterNode(
                name = name,
                url = url,
                username = username,
                password = password,
                certificate = certificate,
                type = type,
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now()
            )
            return try {
                clusterNodeDao.insert(clusterNode)
                    .also { logger.info("Create cluster node [$name] with url [$url] success.") }
                    .let { convert(it)!! }
            } catch (exception: DuplicateKeyException) {
                logger.warn("Insert cluster node [$name] error: [${exception.message}]")
                getByClusterName(name)!!
            }
        }
    }

    fun checkCenterNodeExist(type: ClusterNodeType): Boolean {
        val clusterNodeList = clusterNodeDao.listByNameAndType(name = null, type = type)
        return clusterNodeList.isNotEmpty() && clusterNodeList.size == 1
    }

    override fun deleteById(id: String) {
        getByClusterId(id)?.let {
            clusterNodeRepository.deleteById(id)
        } ?: throw ErrorCodeException(
            ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, "don't find cluster node for id [$id]"
        )
        logger.info("delete cluster node for id [$id] success.")
    }

    override fun tryConnect(name: String) {
        val clusterNodeInfo = convertRemoteInfo(clusterNodeDao.findByName(name))
            ?: throw ErrorCodeException(
                ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, "don't find cluster node [$name]"
            )
        tryConnect(clusterNodeInfo)
    }

    @Suppress("TooGenericExceptionCaught")
    fun tryConnect(remoteClusterInfo: RemoteClusterInfo) {
        with(remoteClusterInfo) {
            try {
                val replicationService = FeignClientFactory.create(ClusterReplicaClient::class.java, this)
                replicationService.ping()
            } catch (exception: RuntimeException) {
                val message = exception.message ?: UNKNOWN
                logger.error("ping cluster [$name] failed, reason: $message")
                throw ErrorCodeException(ReplicationMessageCode.REMOTE_CLUSTER_CONNECT_ERROR, name)
            }
        }
    }

    private fun validateParameter(request: ClusterNodeCreateRequest) {
        with(request) {
            Preconditions.checkNotNull(name, this::name.name)
            Preconditions.checkNotNull(url, this::url.name)
            Preconditions.checkNotNull(type, this::type.name)
            if (!Pattern.matches(CLUSTER_NODE_NAME_PATTERN, name)) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, request::name.name)
            }
            if (!Pattern.matches(CLUSTER_NODE_URL_PATTERN, url)) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, request::url.name)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClusterNodeServiceImpl::class.java)
        private const val CLUSTER_NODE_NAME_PATTERN = "[a-zA-Z_][a-zA-Z0-9\\-_]{1,31}"
        private const val CLUSTER_NODE_URL_PATTERN = "[a-zA-z]+://[^\\s]*"

        private fun convert(tClusterNode: TClusterNode?): ClusterNodeInfo? {
            return tClusterNode?.let {
                ClusterNodeInfo(
                    id = it.id!!,
                    name = it.name,
                    type = it.type,
                    url = it.url,
                    certificate = it.certificate,
                    username = it.username,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME)
                )
            }
        }

        private fun convertRemoteInfo(tClusterNode: TClusterNode?): RemoteClusterInfo? {
            return tClusterNode?.let {
                RemoteClusterInfo(
                    key = it.id!!,
                    name = it.name,
                    url = it.url,
                    certificate = it.certificate,
                    username = it.username,
                    password = it.password
                )
            }
        }
    }
}
