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

package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.replication.model.TReplicaRecord
import com.tencent.bkrepo.replication.model.TReplicaRecordDetail
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordDetailListOption
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordListOption
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where

/**
 * 执行日志查询条件构造工具
 */
object TaskRecordQueryHelper {
    fun recordDetailListQuery(
        recordId: String,
        option: ReplicaRecordDetailListOption
    ): Query {
        val criteria = where(TReplicaRecordDetail::recordId).isEqualTo(recordId)
        buildQueryCriteria(criteria,"packageConstraint.packageKey",option.packageName){
            regex("^$it")
        }
        buildQueryCriteria(criteria, TReplicaRecordDetail::localRepoName.name, option.repoName)
        buildQueryCriteria(criteria, TReplicaRecordDetail::remoteCluster.name, option.clusterName)
        buildQueryCriteria(criteria, "pathConstraint.path", option.path) {
            regex("^$it")
        }
        buildQueryCriteria(criteria, TReplicaRecordDetail::status.name, option.status?.name)
        buildQueryCriteria(criteria, TReplicaRecordDetail::artifactName.name, option.artifactName) {
            regex("^$it")
        }
        buildQueryCriteria(criteria, TReplicaRecordDetail::version.name, option.version)
        return Query(criteria)
            .with(Sort.by(Sort.Order(Sort.Direction.DESC, TReplicaRecordDetail::startTime.name)))
    }

    private fun buildQueryCriteria(
        criteria: Criteria,
        fieldName: String,
        value: String?,
        operation: (Criteria.(String) -> Unit)? = null
    ) {
        value?.let {
            if (operation != null) {
                criteria.and(fieldName).operation(it)
            } else {
                criteria.and(fieldName).isEqualTo(it)
            }
        }
    }

    fun recordListQuery(key: String, option: ReplicaRecordListOption): Query {
        val criteria = with(option) {
            where(TReplicaRecord::taskKey).isEqualTo(key)
                .apply {
                    status?.let { and(TReplicaRecord::status).isEqualTo(it) }
                }
        }
        return Query(criteria)
            .with(Sort.by(Sort.Order(Sort.Direction.DESC, TReplicaRecord::startTime.name)))
    }
}
