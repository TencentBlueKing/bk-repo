/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.matcher.RuleMatcher
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.dao.ReplicaNodeDispatchConfigDao
import com.tencent.bkrepo.replication.enums.DispatchRuleIndex
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TReplicaNodeDispatchConfig
import com.tencent.bkrepo.replication.pojo.dispatch.ReplicaNodeDispatchConfigInfo
import com.tencent.bkrepo.replication.pojo.dispatch.ReplicaNodeDispatchRuleInfo
import com.tencent.bkrepo.replication.pojo.dispatch.request.ReplicaNodeDispatchConfigRequest
import com.tencent.bkrepo.replication.service.ReplicaNodeDispatchService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URL

@Component
class ReplicaNodeDispatchServiceImpl(
    private val replicaNodeDispatchConfigDao: ReplicaNodeDispatchConfigDao,
    private val replicationProperties: ReplicationProperties
): ReplicaNodeDispatchService {


    @Value(SERVER_HOST)
    private lateinit var host: String

    override fun createReplicaNodeDispatchConfig(request: ReplicaNodeDispatchConfigRequest) {
        val configs = replicaNodeDispatchConfigDao.findAllByRuleIndexAndRuleTypeAndNodeUrl(
            request.ruleIndex, request.ruleType, request.nodeUrl
        )
        val config = if (configs.isEmpty()) {
            buildTReplicaNodeDispatchConfig(request)
        } else {
            val config = configs.first()
            config.apply {
                value = request.value
                enable = request.enable
            }
        }
        replicaNodeDispatchConfigDao.save(config)
    }

    override fun deleteReplicaNodeDispatchConfig(id: String) {
        val result = replicaNodeDispatchConfigDao.findById(id)
        if (result.isPresent) {
            replicaNodeDispatchConfigDao.deleteById(id)
        } else {
            throw ErrorCodeException(ReplicationMessageCode.REPLICA_NODE_DISPATCH_CONFIG_NOT_FOUND, id)
        }
        logger.info("delete config for id [$id] success.")
    }

    override fun listAllReplicaNodeDispatchConfig(): List<ReplicaNodeDispatchConfigInfo> {
        return replicaNodeDispatchConfigDao.findAll().map {
            convertTo(it)
        }
    }

    override fun getReplicaNodeDispatchConfig(
        ruleIndex: String, ruleType: OperationType
    ): List<ReplicaNodeDispatchConfigInfo> {
        return replicaNodeDispatchConfigDao.findAllByRuleIndexAndRuleTypeAndEnable(
            ruleIndex, ruleType, true
        ).map {
            convertTo(it)
        }
    }


    override fun <T> findReplicaClientByTargetHost(url: String, target: Class<T>): T? {
        // 账户密码没配置时需要降级为本地执行
        if (replicationProperties.dispatchUser.isNullOrEmpty()
            || replicationProperties.dispatchPwd.isNullOrEmpty()) {
            return null
        }
        val baseUrl = URL(url)
        val ruleInfo = ReplicaNodeDispatchRuleInfo(
            ruleType = OperationType.IN,
            ruleIndex = DispatchRuleIndex.RULE_WITH_HOST,
            ruleValue = baseUrl.host
        )
        return findReplicaClientByRuleIndex(ruleInfo, target)
    }


    private fun <T> findReplicaClientByRuleIndex(ruleInfo: ReplicaNodeDispatchRuleInfo, target: Class<T>): T? {
        with(ruleInfo) {
            val filterConfig = when(ruleIndex) {
                DispatchRuleIndex.RULE_WITH_HOST -> {
                    val rule = buildRule(ruleInfo)
                    val configs = getReplicaNodeDispatchConfig(ruleIndex.value, ruleType)
                    configs.firstOrNull {
                        RuleMatcher.match(rule, mapOf(ruleIndex.value to it.value))
                    }
                }
            } ?: return null
            //  当查询到配置为当前机器时直接执行
            if (selfNode(filterConfig.nodeUrl)) return null
            logger.info("task will be executed with node ${filterConfig.nodeUrl}")
            val clusterInfo = ClusterInfo(
                name = filterConfig.nodeUrl,
                url = filterConfig.nodeUrl,
                username = replicationProperties.dispatchUser,
                password = replicationProperties.dispatchPwd
            )
            return FeignClientFactory.create(target, clusterInfo, normalizeUrl = false)
        }
    }

    private fun selfNode(nodeUrl: String): Boolean {
        if (nodeUrl.contains("$LOCAL_HOST${StringPool.COLON}")) {
            return true
        }
        if (nodeUrl.contains("$LOCAL_HOST_IP${StringPool.COLON}")) {
            return true
        }
        if (nodeUrl.contains("$host${StringPool.COLON}")) {
            return true
        }
        return false
    }

    private fun buildRule(ruleInfo: ReplicaNodeDispatchRuleInfo): Rule {
        with(ruleInfo) {
            val value: Any = when (ruleType) {
                OperationType.IN -> listOf(ruleValue)
                else -> ruleValue
            }
            return Rule.QueryRule(ruleIndex.value, value, ruleType)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaNodeDispatchServiceImpl::class.java)
        private const val SERVER_HOST = "\${spring.cloud.client.ip-address}"
        private const val LOCAL_HOST = "localhost"
        private const val LOCAL_HOST_IP = "127.0.0.1"

        fun buildTReplicaNodeDispatchConfig(
            request: ReplicaNodeDispatchConfigRequest
        ): TReplicaNodeDispatchConfig {
            return TReplicaNodeDispatchConfig(
                nodeUrl = request.nodeUrl,
                ruleIndex = request.ruleIndex,
                ruleType = request.ruleType,
                enable = request.enable,
                value = request.value
            )
        }

        fun convertTo(config: TReplicaNodeDispatchConfig): ReplicaNodeDispatchConfigInfo {
            return ReplicaNodeDispatchConfigInfo(
                id = config.id,
                nodeUrl = config.nodeUrl,
                ruleIndex = config.ruleIndex,
                ruleType = config.ruleType,
                value = config.value,
                enable = config.enable
            )
        }

    }
}


