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

package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.gt
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.lt
import org.springframework.data.mongodb.core.query.where
import java.time.LocalDateTime

object BlockNodeQueryHelper {

    fun listQuery(
        projectId: String,
        repoName: String,
        fullPath: String,
        createdDate: String,
        range: Range
    ): Query {
        val criteria = where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
            .and(TBlockNode::projectId).isEqualTo(projectId)
            .and(TBlockNode::repoName).isEqualTo(repoName)
            .and(TBlockNode::deleted).isEqualTo(null)
            .and(TBlockNode::createdDate).gt(LocalDateTime.parse(createdDate))
            .norOperator(
                TBlockNode::startPos.gt(range.end),
                TBlockNode::endPos.lt(range.start)
            )
            .and(TBlockNode::uploadId).isEqualTo(null)
        val query = Query(criteria).with(Sort.by(TBlockNode::createdDate.name))
        return query
    }

    fun listQueryInUploadId(
        projectId: String,
        repoName: String,
        fullPath: String,
        uploadId: String,
    ):Query {
        val criteria = where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
            .and(TBlockNode::projectId).isEqualTo(projectId)
            .and(TBlockNode::repoName).isEqualTo(repoName)
            .and(TBlockNode::deleted).isEqualTo(null)
            .and(TBlockNode::uploadId).isEqualTo(uploadId)
            .and(TBlockNode::expireDate).gt(LocalDateTime.now())
        return Query(criteria)
    }

    fun fullPathCriteria(projectId: String, repoName: String, fullPath: String, deep: Boolean): Criteria {
        val criteria = if (deep) {
            where(TBlockNode::nodeFullPath).regex("^${EscapeUtils.escapeRegex(fullPath)}/")
        } else {
            where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
        }
        return criteria
            .and(TBlockNode::projectId).isEqualTo(projectId)
            .and(TBlockNode::repoName).isEqualTo(repoName)
            .and(TBlockNode::deleted).isEqualTo(null)
    }

    fun deletedCriteria(
        projectId: String,
        repoName: String,
        fullPath: String,
        nodeCreateDate: LocalDateTime,
        nodeDeleteDate: LocalDateTime
    ): Criteria {
        return where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
            .and(TBlockNode::projectId.name).isEqualTo(projectId)
            .and(TBlockNode::repoName.name).isEqualTo(repoName)
            .and(TBlockNode::createdDate).gt(nodeCreateDate).lt(nodeDeleteDate)
    }

    fun deleteUpdate(): Update {
        return Update().set(TBlockNode::deleted.name, LocalDateTime.now())
    }

    fun moveUpdate(dstFullPath: String): Update {
        return Update().set(TBlockNode::nodeFullPath.name, dstFullPath)
    }

    fun restoreUpdate(): Update {
        return Update().set(TBlockNode::deleted.name, null)
    }

}
