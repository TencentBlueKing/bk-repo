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

package com.tencent.bkrepo.job.migrate.model

import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode.Companion.NEW_FULL_PATH_IDX
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode.Companion.NEW_FULL_PATH_IDX_DEF
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode.Companion.NODE_ID_IDX
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode.Companion.NODE_ID_IDX_DEF
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("migrate_failed_node")
@CompoundIndexes(
    CompoundIndex(name = NEW_FULL_PATH_IDX, def = NEW_FULL_PATH_IDX_DEF, background = true),
    CompoundIndex(name = NODE_ID_IDX, def = NODE_ID_IDX_DEF, unique = true, background = true)
)
data class TMigrateFailedNode(
    val id: String? = null,
    val createdDate: LocalDateTime,
    val lastModifiedDate: LocalDateTime,

    /**
     * 失败node id
     */
    val nodeId: String,
    /**
     * 迁移任务的id
     */
    val taskId: String,
    val projectId: String,
    val repoName: String,
    val fullPath: String,
    val sha256: String,
    val md5: String,
    val size: Long,
    /**
     * 迁移重试次数
     */
    val retryTimes: Int,
    /**
     * 是否正在迁移中
     */
    val migrating: Boolean = false,
) {
    companion object {
        const val NEW_FULL_PATH_IDX = "projectId_repoName_fullPath_retryTimes_idx"
        const val NEW_FULL_PATH_IDX_DEF = "{'projectId': 1, 'repoName': 1, 'fullPath': 1, 'retryTimes': 1}"
        const val NODE_ID_IDX = "nodeId_idx"
        const val NODE_ID_IDX_DEF = "{'nodeId': 1}"
    }
}
