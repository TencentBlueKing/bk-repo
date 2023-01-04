/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.fs.server.model

import com.tencent.bkrepo.common.mongo.reactive.dao.ShardingDocument
import com.tencent.bkrepo.common.mongo.reactive.dao.ShardingKey
import com.tencent.bkrepo.fs.server.model.TBlockNode.Companion.DELETED_IDX
import com.tencent.bkrepo.fs.server.model.TBlockNode.Companion.DELETED_IDX_DEF
import com.tencent.bkrepo.fs.server.model.TBlockNode.Companion.START_POS_IDX
import com.tencent.bkrepo.fs.server.model.TBlockNode.Companion.START_POS_IDX_DEF
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import java.time.LocalDateTime
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes

/**
 * 块节点
 * 数据节点
 * */
@ShardingDocument("block_node")
@CompoundIndexes(
    CompoundIndex(name = START_POS_IDX, def = START_POS_IDX_DEF),
    CompoundIndex(name = DELETED_IDX, def = DELETED_IDX_DEF)
)
data class TBlockNode(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    @ShardingKey(count = SHARDING_COUNT)
    val nodeFullPath: String,
    val nodeSha256: String?,
    val startPos: Long,
    var sha256: String,
    val projectId: String,
    val repoName: String,
    val size: Int,
    val endPos: Long = startPos + size - 1,
    val isDeleted: Boolean
) {
    companion object {
        const val START_POS_IDX = "start_pos_idx"
        const val START_POS_IDX_DEF = "{'projectId': 1, 'repoName': 1,'nodeFullPath':1, 'startPos': 1,'isDeleted': 1}"
        const val DELETED_IDX = "deleted_idx"
        const val DELETED_IDX_DEF = "{'isDeleted': 1}"
    }
}
