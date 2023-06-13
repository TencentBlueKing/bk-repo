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

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.matcher.RuleMatcher
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.dao.ReplicaNodeDispatchConfigDao
import com.tencent.bkrepo.replication.enums.DispatchRuleIndex
import com.tencent.bkrepo.replication.model.TReplicaNodeDispatchConfig
import com.tencent.bkrepo.replication.pojo.dispatch.request.ReplicaNodeDispatchConfigRequest
import com.tencent.bkrepo.replication.pojo.dispatch.ReplicaNodeDispatchConfigInfo
import com.tencent.bkrepo.replication.pojo.dispatch.ReplicaNodeDispatchRuleInfo
import com.tencent.bkrepo.replication.service.ReplicaNodeDispatchService
import org.springframework.stereotype.Component
import java.net.URL

@Component
class ReplicaNodeDispatchServiceImpl(
    private val replicaNodeDispatchConfigDao: ReplicaNodeDispatchConfigDao,
    private val replicationProperties: ReplicationProperties
): ReplicaNodeDispatchService {

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
            val clusterInfo = ClusterInfo(
                name = filterConfig.nodeUrl,
                url = filterConfig.nodeUrl,
                username = replicationProperties.dispatchUser,
                password = replicationProperties.dispatchPwd
            )
            return FeignClientFactory.create(target, clusterInfo, normalizeUrl = false)
        }
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
                nodeUrl = config.nodeUrl,
                ruleIndex = config.ruleIndex,
                ruleType = config.ruleType,
                value = config.value,
                enable = config.enable
            )
        }
    }
}


