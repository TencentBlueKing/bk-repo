package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool.uniqueId
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.replication.dao.ClusterNodeDao
import com.tencent.bkrepo.replication.message.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TClusterNode
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeDeleteRequest
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeType
import com.tencent.bkrepo.replication.pojo.ext.setting.RemoteClusterInfo
import com.tencent.bkrepo.replication.service.ClusterNodeService
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Service
class ClusterNodeServiceImpl(
    private val clusterNodeDao: ClusterNodeDao
) : ClusterNodeService {

    override fun getClusterNodeInfo(name: String): ClusterNodeInfo? {
        return convert(clusterNodeDao.findByName(name))
    }

    override fun checkNameExist(name: String): Boolean {
        return clusterNodeDao.findByName(name) != null
    }

    override fun getCenterNode(): ClusterNodeInfo {
        val clusterInfoList = clusterNodeDao.findByType(ClusterNodeType.MASTER).map { convert(it)!! }
        require(clusterInfoList.size == 1) { "find no or more than one master cluster." }
        return clusterInfoList.first()
    }

    fun checkMasterExist(type: ClusterNodeType): Boolean {
        return clusterNodeDao.findByType(type).isNotEmpty()
    }

    override fun createClusterNode(request: ClusterNodeCreateRequest): ClusterNodeInfo {
        with(request) {
            validateParameter(this)
            if (checkNameExist(name)) {
                throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_EXISTS, name)
            }
            // 主节点唯一
            if (type == ClusterNodeType.MASTER && checkMasterExist(type)) {
                throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_EXISTS, name)
            }
            val clusterNode = TClusterNode(
                key = uniqueId(),
                name = name,
                url = url,
                username = username,
                password = password,
                certificate = certificate,
                type = type,
                createdBy = operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now()
            )
            return try {
                clusterNodeDao.insert(clusterNode)
                    .also { logger.info("Create cluster node [$name] with url [$url] success.") }
                    .let { convert(it)!! }
            } catch (exception: DuplicateKeyException) {
                logger.warn("Insert cluster node [$name] error: [${exception.message}]")
                getClusterNodeInfo(name)!!
            }
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun deleteClusterNode(clusterNodeDeleteRequest: ClusterNodeDeleteRequest) {
        with(clusterNodeDeleteRequest) {
            val clusterNode = checkClusterNode(name)
            clusterNodeDao.deleteById(clusterNode.id)
        }
    }

    override fun listClusterNode(name: String?, type: String?): List<ClusterNodeInfo> {
        val query = buildListQuery(name, type)
        return clusterNodeDao.find(query).map { convert(it)!! }
    }

    override fun listClusterNode(set: Set<String>): List<RemoteClusterInfo> {
        val query = buildListQuery(set)
        return clusterNodeDao.find(query).map { convertRemoteClusterInfo(it)!! }
    }

    override fun listClusterNode(list: List<String>): List<RemoteClusterInfo> {
        val query = buildListQuery(list)
        return clusterNodeDao.find(query).map { convertRemoteClusterInfo(it)!! }
    }

    override fun listClusterNodePage(
        name: String?,
        type: String?,
        pageNumber: Int,
        pageSize: Int
    ): Page<ClusterNodeInfo> {
        val query = buildListQuery(name, type)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val totalRecords = clusterNodeDao.count(query)
        val records = clusterNodeDao.find(query.with(pageRequest)).map { convert(it)!! }
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    override fun detailClusterNode(name: String): ClusterNodeInfo {
        return getClusterNodeInfo(name) ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, name)
    }

    private fun buildListQuery(name: String?, type: String?): Query {
        val criteria = Criteria()
        if (name != null && name.isNotBlank()) {
            criteria.and(TClusterNode::name.name).isEqualTo(name)
        }
        if (type != null && type.isNotBlank()) {
            criteria.and(TClusterNode::type.name).isEqualTo(type.toUpperCase())
        }
        return Query(criteria).with(Sort.by(Sort.Direction.DESC, TClusterNode::createdDate.name))
    }

    private fun buildListQuery(set: Set<String>): Query {
        val criteria = Criteria().and(TClusterNode::name.name).`in`(set).and(TClusterNode::type.name)
            .isEqualTo(ClusterNodeType.SLAVE)
        return Query(criteria).with(Sort.by(Sort.Direction.DESC, TClusterNode::createdDate.name))
    }

    private fun buildListQuery(list: List<String>): Query {
        val criteria = Criteria().and(TClusterNode::key.name).`in`(list).and(TClusterNode::type.name)
            .isEqualTo(ClusterNodeType.SLAVE)
        return Query(criteria).with(Sort.by(Sort.Direction.DESC, TClusterNode::createdDate.name))
    }

    /**
     * 检查节点是否存在，不存在抛异常
     */
    private fun checkClusterNode(name: String): TClusterNode {
        return clusterNodeDao.findByName(name)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, name)
    }

    private fun validateParameter(request: ClusterNodeCreateRequest) {
        with(request) {
            // if (!Pattern.matches(CLUSTER_NODE_NAME_PATTERN, name)) {
            //     throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, request::name.name)
            // }
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
                    key = it.key,
                    name = it.name,
                    url = it.url,
                    certificate = it.certificate,
                    type = it.type,
                    username = it.username,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME)
                )
            }
        }

        private fun convertRemoteClusterInfo(tClusterNode: TClusterNode?): RemoteClusterInfo? {
            return tClusterNode?.let {
                RemoteClusterInfo(
                    key = it.key,
                    name = it.name,
                    url = UrlFormatter.format(it.url) + "replication",
                    certificate = it.certificate,
                    username = it.username,
                    password = it.password
                )
            }
        }
    }
}
