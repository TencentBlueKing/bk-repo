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

package com.tencent.bkrepo.opdata.cluster.topology.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.opdata.cluster.topology.model.TClusterNodeExtension
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 集群节点扩展元数据 DAO。
 */
@Repository
class ClusterNodeExtensionDao : SimpleMongoDao<TClusterNodeExtension>() {

    /**
     * 根据集群名称查询元数据。
     */
    fun findByClusterName(clusterName: String): TClusterNodeExtension? {
        return findOne(Query(TClusterNodeExtension::clusterName.isEqualTo(clusterName)))
    }

    /**
     * 批量查询多个集群名称的元数据。
     */
    fun findByClusterNames(clusterNames: Collection<String>): List<TClusterNodeExtension> {
        if (clusterNames.isEmpty()) return emptyList()
        val criteria = Criteria.where(TClusterNodeExtension::clusterName.name).`in`(clusterNames)
        return find(Query(criteria))
    }

    /**
     * 列出所有元数据。
     */
    fun listAll(): List<TClusterNodeExtension> {
        return find(Query())
    }

    /**
     * upsert 写入元数据：存在则更新指定字段，不存在则插入。
     *
     * region/networkZone/displayName/description 字段允许传入 null 来清除已有值。
     */
    fun upsertByClusterName(
        clusterName: String,
        region: String?,
        networkZone: String?,
        displayName: String?,
        description: String?,
        operator: String
    ) {
        val now = LocalDateTime.now()
        val update = Update()
            .set(TClusterNodeExtension::clusterName.name, clusterName)
            .set(TClusterNodeExtension::region.name, region)
            .set(TClusterNodeExtension::networkZone.name, networkZone)
            .set(TClusterNodeExtension::displayName.name, displayName)
            .set(TClusterNodeExtension::description.name, description)
            .set(TClusterNodeExtension::lastModifiedBy.name, operator)
            .set(TClusterNodeExtension::lastModifiedDate.name, now)
        upsert(
            Query(TClusterNodeExtension::clusterName.isEqualTo(clusterName)),
            update
        )
    }
}
