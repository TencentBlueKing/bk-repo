/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.BasicAuthUtils
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.query.matcher.RuleMatcher
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.dao.ReplicaNodeDispatchConfigDao
import com.tencent.bkrepo.replication.enums.DispatchRuleIndex
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TReplicaNodeDispatchConfig
import com.tencent.bkrepo.replication.pojo.dispatch.ReplicaNodeDispatchConfigInfo
import com.tencent.bkrepo.replication.pojo.dispatch.request.ReplicaNodeDispatchConfigCreateRequest
import com.tencent.bkrepo.replication.pojo.dispatch.request.ReplicaNodeDispatchConfigUpdateRequest
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaNodeDispatchService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.net.URL

/**
 * 分发任务执行服务器对应调度逻辑处理接口
 */
@Component
class ReplicaNodeDispatchServiceImpl(
    private val replicaNodeDispatchConfigDao: ReplicaNodeDispatchConfigDao,
    private val clusterNodeService: ClusterNodeService,
    private val replicationProperties: ReplicationProperties
): ReplicaNodeDispatchService {


    @Value(SERVER_HOST)
    private lateinit var serverIp: String

    override fun createReplicaNodeDispatchConfig(request: ReplicaNodeDispatchConfigCreateRequest) {
        replicaNodeDispatchConfigDao.save(buildTReplicaNodeDispatchConfig(request))
    }

    override fun updateReplicaNodeDispatchConfig(request: ReplicaNodeDispatchConfigUpdateRequest) {
        val config = replicaNodeDispatchConfigDao.findByIdOrNull(request.id)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_NODE_DISPATCH_CONFIG_NOT_FOUND, request.id)
        config.apply {
            rule = request.rule.toJsonString()
            enable = request.enable
        }
        replicaNodeDispatchConfigDao.save(config)
    }

    override fun deleteReplicaNodeDispatchConfig(id: String) {
        replicaNodeDispatchConfigDao.findByIdOrNull(id)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_NODE_DISPATCH_CONFIG_NOT_FOUND, id)
        replicaNodeDispatchConfigDao.deleteById(id)
        logger.info("delete config for id [$id] success.")
    }

    override fun listAllReplicaNodeDispatchConfig(): List<ReplicaNodeDispatchConfigInfo> {
        return replicaNodeDispatchConfigDao.findAll().map {
            convertTo(it)
        }
    }

    /**
     * 读取配置中的字段，然后根据对应字段获取数据然后进行比较
     */
    override fun <T> findReplicaClient(taskDetail: ReplicaTaskDetail, target: Class<T>): T? {
        if (!checkProperties()) return null
        val valuesToMatch = buildValuesToMatch(taskDetail)
        return findReplicaClientByRule(valuesToMatch, target)
    }
    override fun <T> findReplicaClientByHost(host: String, target: Class<T>): T? {
        if (!checkProperties()) return null
        val baseUrl = URL(host)
        return findReplicaClientByRule(mapOf(DispatchRuleIndex.RULE_WITH_HOST.value to baseUrl.host), target)
    }

    private fun buildValuesToMatch(taskDetail: ReplicaTaskDetail): Map<String, Any> {
        val valuesToMatch = mutableMapOf<String, Any>()
        DispatchRuleIndex.values().forEach {
            when (it) {
                DispatchRuleIndex.RULE_WITH_HOST -> {
                    val remoteCluster = taskDetail.task.remoteClusters.first()
                    val clusterInfo = clusterNodeService.getByClusterId(remoteCluster.id)
                        ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, remoteCluster.id)
                    val baseUrl = URL(clusterInfo.url)
                    valuesToMatch[DispatchRuleIndex.RULE_WITH_HOST.value] = baseUrl.host
                }
                DispatchRuleIndex.RULE_WITH_PROJECT -> {
                    valuesToMatch[DispatchRuleIndex.RULE_WITH_PROJECT.value] = taskDetail.task.projectId
                }
                DispatchRuleIndex.RULE_WITH_SIZE -> {
                    valuesToMatch[DispatchRuleIndex.RULE_WITH_SIZE.value] = taskDetail.task.totalBytes ?: 0
                }
                else -> throw UnsupportedOperationException()
            }
        }
        return valuesToMatch
    }


    /**
     * 账户密码没配置时需要降级为本地执行
     */
    private fun checkProperties(): Boolean {
        if (replicationProperties.dispatchUser.isNullOrEmpty()
            || replicationProperties.dispatchPwd.isNullOrEmpty()) {
            return false
        }
        return true
    }

    private fun <T> findReplicaClientByRule(valuesToMatch: Map<String, Any>, target: Class<T>): T? {
        if (valuesToMatch.isEmpty()) return null
        val configNodes = listEnableReplicaNodeDispatchConfig().filter {
            RuleMatcher.match(it.rule, valuesToMatch)
        }
        val filterNode = filterNodes(configNodes) ?: return null
        logger.info("task will be executed with node ${filterNode.nodeUrl}")
        val clusterInfo = ClusterInfo(
            name = filterNode.nodeUrl,
            url = filterNode.nodeUrl,
            username = replicationProperties.dispatchUser,
            password = replicationProperties.dispatchPwd
        )
        return FeignClientFactory.create(target, clusterInfo, normalizeUrl = false)
    }

    /**
     * 从查询出的配置中取出对应的节点信息
     */
    private fun filterNodes(configNodes: List<ReplicaNodeDispatchConfigInfo>): ReplicaNodeDispatchConfigInfo? {
        val filterList = mutableListOf<String>()
        var config = configNodes.randomOrNull() ?: return null
        filterList.add(config.id!!)
        while(
            !selfNode(config.nodeUrl) &&
            !filterHealthyNodes(config, replicationProperties.dispatchUser!!, replicationProperties.dispatchPwd!!)
        ) {
            val remainConfigNodes = configNodes.filter { !filterList.contains(it.id) }
            if (remainConfigNodes.isEmpty()) return null
            config = remainConfigNodes.random()
            filterList.add(config.id!!)
        }
        //  当查询到配置为当前机器时直接执行
        if (selfNode(config.nodeUrl)) return null
        return config
    }



    /**
     * 通过ping接口过滤出可访问的节点
     */
    private fun filterHealthyNodes(
        config: ReplicaNodeDispatchConfigInfo,
        username: String,
        password: String
    ): Boolean {
        val remoteClusterInfo = ClusterInfo(
            name = config.nodeUrl,
            url = config.nodeUrl,
            username = username,
            password = password
        )
        return try {
            val replicationService = FeignClientFactory.create(
                ArtifactReplicaClient::class.java, remoteClusterInfo, normalizeUrl = false
            )
            val token = BasicAuthUtils.encode(username, password)
            replicationService.ping(token)
            true
        } catch (ignore: Exception) {
            logger.info("ping node ${config.nodeUrl} failed")
            false
        }
    }


    private fun listEnableReplicaNodeDispatchConfig(): List<ReplicaNodeDispatchConfigInfo> {
        return replicaNodeDispatchConfigDao.findAllByEnable(true).map {
            convertTo(it)
        }
    }

    /**
     * 判断配置的执行节点是否为本机
     * localhost:port/127.0.0.1:port/当前节点ip:port
     */
    private fun selfNode(nodeUrl: String): Boolean {
        val host = URL(nodeUrl).host
        return listOf(LOCAL_HOST, LOCAL_HOST_IP, serverIp).contains(host)
    }


    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaNodeDispatchServiceImpl::class.java)
        private const val SERVER_HOST = "\${spring.cloud.client.ip-address}"
        private const val LOCAL_HOST = "localhost"
        private const val LOCAL_HOST_IP = "127.0.0.1"

        fun buildTReplicaNodeDispatchConfig(
            request: ReplicaNodeDispatchConfigCreateRequest
        ): TReplicaNodeDispatchConfig {
            return TReplicaNodeDispatchConfig(
                nodeUrl = request.nodeUrl,
                rule = request.rule.toJsonString(),
                enable = request.enable
            )
        }

        fun convertTo(config: TReplicaNodeDispatchConfig): ReplicaNodeDispatchConfigInfo {
            return ReplicaNodeDispatchConfigInfo(
                id = config.id,
                nodeUrl = config.nodeUrl,
                rule = config.rule.readJsonString<Rule>(),
                enable = config.enable
            )
        }

    }
}


