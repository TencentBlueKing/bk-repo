/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata.model

import com.tencent.bkrepo.common.metadata.constant.SHARDING_COUNT
import com.tencent.bkrepo.common.metadata.model.TBlockNode.Companion.BLOCK_IDX
import com.tencent.bkrepo.common.metadata.model.TBlockNode.Companion.BLOCK_IDX_DEF
import com.tencent.bkrepo.common.mongo.reactive.dao.ShardingDocument
import com.tencent.bkrepo.common.mongo.reactive.dao.ShardingKey
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import java.time.LocalDateTime

/**
 * 块节点
 * */
@ShardingDocument("block_node")
@CompoundIndexes(
    CompoundIndex(name = BLOCK_IDX, def = BLOCK_IDX_DEF)
)
data class TBlockNode(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    val nodeFullPath: String,
    val startPos: Long,
    var sha256: String,
    val projectId: String,
    @ShardingKey(count = SHARDING_COUNT)
    val repoName: String,
    val size: Long,
    val endPos: Long = startPos + size - 1,
    var deleted: LocalDateTime? = null
) {
    companion object {
        const val BLOCK_IDX = "start_pos_idx"
        const val BLOCK_IDX_DEF = "{'projectId': 1, 'repoName': 1,'nodeFullPath':1, 'startPos': 1, 'isDeleted': 1}"
    }
}
