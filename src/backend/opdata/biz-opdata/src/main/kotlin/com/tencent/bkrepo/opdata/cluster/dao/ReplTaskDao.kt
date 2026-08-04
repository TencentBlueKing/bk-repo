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

package com.tencent.bkrepo.opdata.cluster.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskRecord
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

/**
 * replication 模块 replica_task 集合的只读 DAO。
 *
 * 反序列化目标 [ReplicaTaskRecord] 来自 replication-api 的只读 pojo（无 @Document），
 * 集合名通过覆写 [collectionName] 显式声明。
 */
@Repository
class ReplTaskDao : SimpleMongoDao<ReplicaTaskRecord>() {

    override val collectionName: String = ReplicaTaskRecord.COLLECTION_NAME

    /**
     * 列出所有任务。任务总数预期百级，可一次性加载到内存做聚合。
     */
    fun listAll(): List<ReplicaTaskRecord> {
        return find(Query())
    }

    /**
     * 查询关联指定远程集群名称的任务（用于 REMOTE 节点关联任务列表）。
     */
    fun listByRemoteClusterName(name: String): List<ReplicaTaskRecord> {
        val criteria = Criteria.where("${ReplicaTaskRecord::remoteClusters.name}.name").isEqualTo(name)
        return find(Query(criteria))
    }

    /**
     * 统计关联指定远程集群名称的任务数量。
     */
    fun countByRemoteClusterName(name: String): Long {
        val criteria = Criteria.where("${ReplicaTaskRecord::remoteClusters.name}.name").isEqualTo(name)
        return count(Query(criteria))
    }

    /**
     * 查询关联任意一个 REMOTE 集群的任务（用于聚合统计）。
     */
    fun listByRemoteClusterNames(names: Collection<String>): List<ReplicaTaskRecord> {
        if (names.isEmpty()) return emptyList()
        val criteria = Criteria.where("${ReplicaTaskRecord::remoteClusters.name}.name").`in`(names)
        return find(Query(criteria))
    }

    /**
     * 分页查询任务（按最近修改时间倒序）。
     */
    fun pageByRemoteClusterName(name: String, pageNumber: Int, pageSize: Int): List<ReplicaTaskRecord> {
        val criteria = Criteria.where("${ReplicaTaskRecord::remoteClusters.name}.name").isEqualTo(name)
        val query = Query(criteria)
            .with(Sort.by(Sort.Direction.DESC, ReplicaTaskRecord::lastModifiedDate.name))
            .with(PageRequest.of(pageNumber - 1, pageSize))
        return find(query)
    }
}
