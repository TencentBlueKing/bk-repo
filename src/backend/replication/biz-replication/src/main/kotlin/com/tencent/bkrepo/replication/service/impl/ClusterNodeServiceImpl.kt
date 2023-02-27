/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool.UNKNOWN
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.BasicAuthUtils
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.RsaUtils
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.common.storage.innercos.retry
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.dao.ClusterNodeDao
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TClusterNode
import com.tencent.bkrepo.replication.pojo.cluster.ClusterListOption
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeName
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeStatusUpdateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeUpdateRequest
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.util.ClusterQueryHelper
import com.tencent.bkrepo.replication.util.HttpUtils
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Service
class ClusterNodeServiceImpl(
    private val clusterNodeDao: ClusterNodeDao
) : ClusterNodeService {

    override fun getByClusterId(id: String): ClusterNodeInfo? {
        return convert(clusterNodeDao.findById(id))
    }

    override fun getClusterNameById(id: String): ClusterNodeName {
        return convertNodeName(clusterNodeDao.findById(id))
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, id)
    }

    override fun getByClusterName(name: String): ClusterNodeInfo? {
        return convert(clusterNodeDao.findByName(name))
    }

    override fun getCenterNode(): ClusterNodeInfo {
        val clusterInfoList = clusterNodeDao.listByNameAndType(type = ClusterNodeType.CENTER)
        require(clusterInfoList.size == 1) { "find no or more than one center cluster node." }
        return clusterInfoList.map { convert(it)!! }.first()
    }

    override fun listEdgeNodes(): List<ClusterNodeInfo> {
        return clusterNodeDao.listByNameAndType(type = ClusterNodeType.EDGE).map { convert(it)!! }
    }

    override fun listClusterNodes(name: String?, type: ClusterNodeType?): List<ClusterNodeInfo> {
        return clusterNodeDao.listByNameAndType(name, type).map { convert(it)!! }
    }

    override fun listClusterNodesPage(option: ClusterListOption): Page<ClusterNodeInfo> {
        val pageRequest = Pages.ofRequest(option.pageNumber, option.pageSize)
        val query = ClusterQueryHelper.clusterListQuery(option.name, option.type)
        val totalRecords = clusterNodeDao.count(query)
        val records = clusterNodeDao.find(query.with(pageRequest)).map { convert(it)!! }
        return Pages.ofResponse(pageRequest, totalRecords, records)
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
            if (type == ClusterNodeType.CENTER && checkCenterNodeExist()) {
                throw ErrorCodeException(ReplicationMessageCode.CLUSTER_CENTER_NODE_EXISTS, name)
            }
            // password加密存储
            val clusterNode = TClusterNode(
                name = name,
                status = ClusterNodeStatus.HEALTHY,
                url = UrlFormatter.formatUrl(url),
                username = username,
                password = crypto(password, false),
                certificate = certificate,
                type = type,
                appId = appId,
                accessKey = accessKey,
                secretKey = secretKey,
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now(),
            )
            // 检测远程集群网络连接是否可用
            retry(times = RETRY_COUNT, delayInSeconds = DELAY_IN_SECONDS) {
                tryConnect(convert(clusterNode)!!)
            }
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

    override fun update(request: ClusterNodeUpdateRequest): ClusterNodeInfo {
        with(request) {
            val tClusterNode = clusterNodeDao.findByName(name)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, name)
            Preconditions.checkNotBlank(url, this::url.name)
            if (!Pattern.matches(CLUSTER_NODE_URL_PATTERN, url)) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, request::url.name)
            }
            tClusterNode.url = url!!
            tClusterNode.apply {
                username = request.username
                password = crypto(request.password, false)
                certificate = request.certificate
            }
            // 检测远程集群网络连接是否可用
            retry(times = RETRY_COUNT, delayInSeconds = DELAY_IN_SECONDS) {
                tryConnect(convert(tClusterNode)!!)
            }
            return try {
                clusterNodeDao.save(tClusterNode)
                    .also { logger.info("Update cluster node [$name] with url [$url] success.") }
                    .let { convert(it)!! }
            } catch (exception: DuplicateKeyException) {
                logger.warn("update cluster node [$name] error: [${exception.message}]")
                getByClusterName(name)!!
            }
        }
    }

    override fun deleteById(id: String) {
        getByClusterId(id)?.let {
            clusterNodeDao.removeById(id)
        } ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, id)
        logger.info("delete cluster node for id [$id] success.")
    }

    override fun tryConnect(clusterNodeInfo: ClusterNodeInfo) {
        with(clusterNodeInfo) {
            val clusterInfo = convertRemoteInfo(clusterNodeInfo)
            if (ClusterNodeType.REMOTE == type) {
                tryConnectRemoteCluster(clusterInfo)
            } else {
                tryConnectNonRemoteCluster(clusterInfo)
            }
        }
    }

    override fun tryConnect(name: String) {
        val node = clusterNodeDao.findByName(name)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, name)
        tryConnect(convert(node)!!)
    }

    override fun updateClusterNodeStatus(request: ClusterNodeStatusUpdateRequest) {
        with(request) {
            val tClusterNode = clusterNodeDao.findByName(name)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, name)
            val clusterNode = tClusterNode.copy(
                status = status,
                errorReason = errorReason,
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now()
            )
            clusterNodeDao.save(clusterNode)
            logger.info("update cluster [$name] status from [${tClusterNode.status}] to [$status] success.")
        }
    }

    /**
     * 针对非third party集群做连接判断
     */
    fun tryConnectNonRemoteCluster(remoteClusterInfo: ClusterInfo) {
        with(remoteClusterInfo) {
            try {
                val replicationService = FeignClientFactory.create(ArtifactReplicaClient::class.java, this)
                val authToken = BasicAuthUtils.encode(username.orEmpty(), password.orEmpty())
                replicationService.ping(authToken)
            } catch (exception: RuntimeException) {
                val message = exception.message ?: UNKNOWN
                logger.warn("ping cluster [$name] failed, reason: $message")
                throw ErrorCodeException(ReplicationMessageCode.REMOTE_CLUSTER_CONNECT_ERROR, name.orEmpty())
            }
        }
    }

    /**
     * 针对third party集群做额外的判断
     */
    fun tryConnectRemoteCluster(remoteClusterInfo: ClusterInfo) {
        with(remoteClusterInfo) {
            try {
                HttpUtils.pingURL(remoteClusterInfo.url, 60000)
            } catch (exception: Exception) {
                val message = exception.message ?: UNKNOWN
                logger.warn("ping remote cluster [$name] failed, reason: $message")
                throw ErrorCodeException(ReplicationMessageCode.REMOTE_CLUSTER_CONNECT_ERROR, name.orEmpty())
            }
        }
    }

    private fun checkCenterNodeExist(): Boolean {
        val clusterNodeList = clusterNodeDao.listByNameAndType(type = ClusterNodeType.CENTER)
        return clusterNodeList.isNotEmpty() && clusterNodeList.size == 1
    }

    private fun validateParameter(request: ClusterNodeCreateRequest) {
        with(request) {
            Preconditions.checkNotBlank(name, this::name.name)
            Preconditions.checkNotBlank(url, this::url.name)
            Preconditions.checkNotBlank(type, this::type.name)
            if (name.length < CLUSTER_NAME_LENGTH_MIN ||
                name.length > CLUSTER_NAME_LENGTH_MAX
            ) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, request::name.name)
            }
            if (!Pattern.matches(CLUSTER_NODE_URL_PATTERN, url)) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, request::url.name)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClusterNodeServiceImpl::class.java)
        private const val CLUSTER_NODE_URL_PATTERN = "[a-zA-z]+://[^\\s]*"
        private const val CLUSTER_NAME_LENGTH_MIN = 2
        private const val CLUSTER_NAME_LENGTH_MAX = 256
        private const val RETRY_COUNT = 2
        private const val DELAY_IN_SECONDS: Long = 1

        private fun convert(tClusterNode: TClusterNode?): ClusterNodeInfo? {
            return tClusterNode?.let {
                ClusterNodeInfo(
                    id = it.id,
                    name = it.name,
                    status = it.status,
                    errorReason = it.errorReason,
                    type = it.type,
                    url = it.url,
                    username = it.username,
                    password = crypto(it.password, true),
                    certificate = it.certificate,
                    appId = it.appId,
                    accessKey = it.accessKey,
                    secretKey = it.secretKey,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME)
                )
            }
        }

        private fun convertRemoteInfo(tClusterNode: ClusterNodeInfo): ClusterInfo {
            return tClusterNode.let {
                ClusterInfo(
                    name = it.name,
                    url = it.url,
                    certificate = it.certificate,
                    username = it.username,
                    password = crypto(it.password, true),
                    appId = it.appId,
                    accessKey = it.accessKey,
                    secretKey = it.secretKey
                )
            }
        }

        private fun convertNodeName(tClusterNode: TClusterNode?): ClusterNodeName? {
            return tClusterNode?.let {
                ClusterNodeName(
                    id = it.id!!,
                    name = it.name
                )
            }
        }

        /**
         * 加解密
         */
        private fun crypto(pw: String?, decrypt: Boolean): String? {
            if (pw.isNullOrBlank()) return pw
            return if (!decrypt) {
                RsaUtils.encrypt(pw)
            } else {
                try {
                    RsaUtils.decrypt(pw)
                } catch (e: Exception) {
                    pw
                }
            }
        }
    }
}
