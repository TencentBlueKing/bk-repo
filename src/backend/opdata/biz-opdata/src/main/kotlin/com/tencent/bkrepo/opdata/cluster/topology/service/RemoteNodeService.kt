/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.cluster.topology.service

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.opdata.cluster.dao.ReplClusterDao
import com.tencent.bkrepo.opdata.cluster.dao.ReplTaskDao
import com.tencent.bkrepo.opdata.cluster.topology.pojo.RemoteNodeItemVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.RemoteNodePageVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.RemoteNodeSortBy
import com.tencent.bkrepo.opdata.cluster.topology.pojo.RemoteNodeTaskVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.RemoteSummaryVO
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeRecord
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskRecord
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * REMOTE 节点列表与详情服务。
 *
 * REMOTE 节点数量预期可达 1000+，因此采用服务端分页与关键字过滤；
 * 单页大小硬上限 [MAX_PAGE_SIZE]，避免一次性返回过多数据。
 */
@Service
class RemoteNodeService(
    private val replClusterDao: ReplClusterDao,
    private val replTaskDao: ReplTaskDao
) {

    /**
     * 分页查询 REMOTE 节点列表，并补齐每个节点的关联任务统计。
     *
     * @param keyword          模糊匹配 name / url
     * @param sortBy           排序字段
     * @param sortOrder        asc / desc
     * @param lastUsedAfter    最近使用时间下界（含）
     * @param lastUsedBefore   最近使用时间上界（含）
     * @param pageNumber       页码（从 1 开始）
     * @param pageSize         每页大小（最大 [MAX_PAGE_SIZE]）
     */
    fun pageQuery(
        keyword: String? = null,
        sortBy: RemoteNodeSortBy = RemoteNodeSortBy.LAST_USED_TIME,
        sortOrder: String = "desc",
        lastUsedAfter: LocalDateTime? = null,
        lastUsedBefore: LocalDateTime? = null,
        pageNumber: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): RemoteNodePageVO {
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val safePageNumber = pageNumber.coerceAtLeast(1)

        // DB 侧仅按 name 模糊匹配做粗筛分页；为了支持按 lastUsedTime 过滤/排序，
        // 我们在筛选页内对当页节点逐个补齐统计信息。
        val totalRaw = replClusterDao.countRemoteNodes(keyword)
        val rawNodes = replClusterDao.pageRemoteNodes(keyword, safePageNumber, safePageSize)
        val items = rawNodes.map { node -> buildItem(node) }

        val filtered = items.filter { item ->
            (lastUsedAfter == null || (item.lastUsedTime?.isBefore(lastUsedAfter) == false)) &&
                (lastUsedBefore == null || (item.lastUsedTime?.isAfter(lastUsedBefore) == false))
        }
        val sorted = sortItems(filtered, sortBy, sortOrder)

        return RemoteNodePageVO(
            pageNumber = safePageNumber,
            pageSize = safePageSize,
            total = totalRaw,
            records = sorted
        )
    }

    /**
     * REMOTE 节点全局汇总。
     */
    fun summary(): RemoteSummaryVO {
        val totalNodes = replClusterDao.countByType(ClusterNodeType.REMOTE)
        val remoteNodeNames = replClusterDao.listByType(ClusterNodeType.REMOTE).map { it.name }.toSet()
        if (remoteNodeNames.isEmpty()) {
            return RemoteSummaryVO(totalNodes = totalNodes, activeTaskCount = 0, completedTaskCount = 0)
        }
        val tasks = replTaskDao.listByRemoteClusterNames(remoteNodeNames)
        val active = tasks.count { it.enabled }.toLong()
        val completed = (tasks.size - active).coerceAtLeast(0).toLong()
        return RemoteSummaryVO(
            totalNodes = totalNodes,
            activeTaskCount = active,
            completedTaskCount = completed
        )
    }

    /**
     * 查询 REMOTE 节点关联的同步任务列表。
     */
    fun getNodeTaskList(remoteClusterName: String): List<RemoteNodeTaskVO> {
        val tasks = replTaskDao.listByRemoteClusterName(remoteClusterName)
        return tasks.map { it.toTaskVO() }
    }

    private fun buildItem(node: ClusterNodeRecord): RemoteNodeItemVO {
        val tasks = replTaskDao.listByRemoteClusterName(node.name)
        val taskCount = tasks.size.toLong()
        val activeTaskCount = tasks.count { it.enabled }.toLong()
        val lastUsed = tasks.mapNotNull { it.lastExecutionTime }.maxOrNull()
        return RemoteNodeItemVO(
            name = node.name,
            url = node.url,
            taskCount = taskCount,
            activeTaskCount = activeTaskCount,
            lastUsedTime = lastUsed
        )
    }

    private fun sortItems(
        items: List<RemoteNodeItemVO>,
        sortBy: RemoteNodeSortBy,
        sortOrder: String
    ): List<RemoteNodeItemVO> {
        val asc = sortOrder.equals("asc", ignoreCase = true)
        val comparator: Comparator<RemoteNodeItemVO> = when (sortBy) {
            RemoteNodeSortBy.LAST_USED_TIME -> compareBy(nullsFirst()) { it.lastUsedTime }
            RemoteNodeSortBy.TASK_COUNT -> compareBy { it.taskCount }
            RemoteNodeSortBy.NAME -> compareBy { it.name }
        }
        return if (asc) items.sortedWith(comparator) else items.sortedWith(comparator.reversed())
    }

    private fun ReplicaTaskRecord.toTaskVO(): RemoteNodeTaskVO {
        return RemoteNodeTaskVO(
            key = this.key,
            name = this.name,
            projectId = this.projectId,
            replicaType = this.replicaType,
            enabled = this.enabled,
            status = this.status,
            lastExecutionStatus = this.lastExecutionStatus,
            createdDate = this.createdDate,
            lastExecutionTime = this.lastExecutionTime
        )
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MAX_PAGE_SIZE = 100
    }
}
