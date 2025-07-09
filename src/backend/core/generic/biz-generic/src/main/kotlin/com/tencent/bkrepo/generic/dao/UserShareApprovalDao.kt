/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.generic.dao

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.generic.model.TUserShareApproval
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
@Conditional(SyncCondition::class)
class UserShareApprovalDao : SimpleMongoDao<TUserShareApproval>() {

    fun findByApprovalId(approvalId: String, downloadUser: String): TUserShareApproval? {
        val query = Query(
            where(TUserShareApproval::approvalId).isEqualTo(approvalId).and(TUserShareApproval::downloadUserId)
                .isEqualTo(downloadUser)
        )
        return findOne(query)
    }

    fun findByShareId(shareId: String, downloadUser: String): TUserShareApproval? {
        val query = Query(
            where(TUserShareApproval::shareId).isEqualTo(shareId).and(TUserShareApproval::downloadUserId)
                .isEqualTo(downloadUser)
        )
        return findOne(query)
    }

    fun approve(approvalId: String, approveUserId: String): Boolean {
        val query = Query(where(TUserShareApproval::approvalId).isEqualTo(approvalId))
        val update = Update.update(TUserShareApproval::approved.name, true)
            .set(TUserShareApproval::approveUserId.name, approveUserId)
            .set(TUserShareApproval::approveDate.name, LocalDateTime.now())
        return updateFirst(query, update).modifiedCount == 1L
    }
}
