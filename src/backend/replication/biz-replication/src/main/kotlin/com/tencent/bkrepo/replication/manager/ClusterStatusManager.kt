/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.manager

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.StandaloneJob
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeStatusUpdateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.DetectType
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * 集群状态管理类
 */
@Component
class ClusterStatusManager(
    private val clusterNodeService: ClusterNodeService,
    private val clusterProperties: ClusterProperties
) {
    /**
     * 进程内失败计数器：节点名 -> 连续 ping 失败次数。
     * 仅用于 ping 防抖，不持久化；每轮巡检结束会按当前节点列表清理残留。
     */
    private val failureCounter = ConcurrentHashMap<String, Int>()

    @Scheduled(initialDelay = 600 * 1000L, fixedDelay = 600 * 1000L) // 每隔10min检测一次
    @StandaloneJob
    fun start() {
        val clusterNodeList = clusterNodeService.listClusterNodes(name = null, type = null)
        val selfName = clusterProperties.self.name
        val targets = if (selfName.isNullOrBlank()) {
            logger.warn(
                "clusterProperties.self.name is blank, self-node filter is disabled; " +
                    "self ping/report check will not be skipped"
            )
            clusterNodeList
        } else {
            clusterNodeList.filter { it.name != selfName }
        }
        targets.forEach {
            when (it.detectType) {
                null, DetectType.PING -> ping(it)
                DetectType.REPORT -> report(it)
            }
        }
        // 清理已不在巡检列表中的失败计数（如节点被删除、或被自身过滤后不再参与计数）
        failureCounter.keys.retainAll(targets.map { it.name }.toSet())
    }

    fun ping(it: ClusterNodeInfo) {
        try {
            clusterNodeService.tryConnect(it)
            // ping 成功：清零失败计数；若原来是 UNHEALTHY 则立即翻回 HEALTHY
            failureCounter.remove(it.name)
            if (it.status == ClusterNodeStatus.UNHEALTHY) {
                updateClusterNodeStatus(it.name, ClusterNodeStatus.HEALTHY)
            }
        } catch (exception: ErrorCodeException) {
            // ping 失败：累加失败计数，仅当连续失败达到阈值才将 HEALTHY 翻为 UNHEALTHY
            val current = failureCounter.merge(it.name, 1, Int::plus) ?: 1
            if (it.status == ClusterNodeStatus.HEALTHY && current >= FAILURE_THRESHOLD) {
                updateClusterNodeStatus(it.name, ClusterNodeStatus.UNHEALTHY, exception.message)
            } else {
                logger.info(
                    "ping cluster node [${it.name}] failed ($current/$FAILURE_THRESHOLD): ${exception.message}"
                )
            }
        }
    }

    private fun report(it: ClusterNodeInfo) {
        if (clusterProperties.role != ClusterNodeType.CENTER) {
            return
        }
        val oneMinuteBefore = LocalDateTime.now().minusMinutes(1)
        val lastReportTime = it.lastReportTime ?: LocalDateTime.parse(it.createdDate)
        if (it.status == ClusterNodeStatus.HEALTHY && lastReportTime.isBefore(oneMinuteBefore)) {
            logger.warn("edge node [${it.name}] change to unhealthy, last report time ${it.lastReportTime}")
            return updateClusterNodeStatus(it.name, ClusterNodeStatus.UNHEALTHY)
        }

        if (it.status == ClusterNodeStatus.UNHEALTHY && lastReportTime.isAfter(oneMinuteBefore)) {
            logger.info("edge node [${it.name}] change to healthy, last report time ${it.lastReportTime}")
            return updateClusterNodeStatus(it.name, ClusterNodeStatus.HEALTHY)
        }
    }

    /**
     * 修改节点状态
     */
    private fun updateClusterNodeStatus(name: String, status: ClusterNodeStatus, errorReason: String? = null) {
        val request = ClusterNodeStatusUpdateRequest(
            name = name,
            status = status,
            errorReason = errorReason,
            operator = SYSTEM_USER
        )
        clusterNodeService.updateClusterNodeStatus(request)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClusterStatusManager::class.java)
        private const val FAILURE_THRESHOLD = 3
    }
}
