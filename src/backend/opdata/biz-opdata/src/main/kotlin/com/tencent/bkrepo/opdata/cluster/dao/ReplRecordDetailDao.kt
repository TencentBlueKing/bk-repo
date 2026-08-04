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
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordDetailInfo
import org.bson.Document
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

/**
 * replication 模块 replica_record_detail 集合的只读 DAO。
 *
 * 主要服务于 [com.tencent.bkrepo.opdata.cluster.topology.service.TrafficStatsService]
 * 进行流量聚合查询。聚合阶段（如 \$dateTrunc）走原生 MongoDB driver，便于设置 maxTimeMS
 * 与 allowDiskUse 等参数。
 *
 * 反序列化目标 [ReplicaRecordDetailInfo] 来自 replication-api 的只读 pojo（无 @Document），
 * 集合名通过覆写 [collectionName] 显式声明。
 */
@Repository
class ReplRecordDetailDao : SimpleMongoDao<ReplicaRecordDetailInfo>() {

    override val collectionName: String = ReplicaRecordDetailInfo.COLLECTION_NAME

    /**
     * 通过原生 driver 执行 aggregate，并设置 maxTimeMS 与 allowDiskUse。
     *
     * Spring Data Mongo 的 Criteria 不便表达 \$dateTrunc 等高级阶段，
     * 故直接传入构造好的 [Document] pipeline。
     *
     * @param pipeline 构造好的聚合阶段列表
     * @param maxTimeMs 服务端最大执行时间（毫秒），超时由 MongoDB 端中断
     * @param allowDiskUse 是否允许聚合阶段使用磁盘（默认开启，避免内存超限）
     */
    fun aggregate(
        pipeline: List<Document>,
        maxTimeMs: Long,
        allowDiskUse: Boolean = true
    ): List<Document> {
        val collection = determineMongoTemplate().getCollection(collectionName)
        val iterable = collection.aggregate(pipeline)
            .maxTime(maxTimeMs, TimeUnit.MILLISECONDS)
            .allowDiskUse(allowDiskUse)
        val list = mutableListOf<Document>()
        iterable.iterator().use { cursor ->
            while (cursor.hasNext()) {
                list.add(cursor.next())
            }
        }
        return list
    }
}
