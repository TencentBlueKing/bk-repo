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

package com.tencent.bkrepo.replication.pojo.record

import java.time.LocalDateTime

/**
 * `replica_record_detail` 集合的只读投影 pojo。
 *
 * 仅供外部模块（如 opdata 通道流量统计）跨模块只读访问使用，
 * 不带 @Document，不参与 replication 模块自身的持久化映射；
 * 仅保留流量统计所需的核心字段（localCluster / remoteCluster / size / startTime / status），
 * 状态字段使用 String 弱绑定避免与 ExecutionStatus 强耦合。
 *
 * @see com.tencent.bkrepo.replication.model.TReplicaRecordDetail replication 模块内部权威实体
 */
data class ReplicaRecordDetailInfo(
    var id: String? = null,
    var recordId: String? = null,
    var localCluster: String? = null,
    var remoteCluster: String? = null,
    var size: Long? = null,
    /** 取值范围：RUNNING / SUCCESS / FAILED 等 */
    var status: String? = null,
    var startTime: LocalDateTime? = null,
    var endTime: LocalDateTime? = null
) {
    companion object {
        /** 对应 MongoDB 集合名 */
        const val COLLECTION_NAME = "replica_record_detail"
    }
}
