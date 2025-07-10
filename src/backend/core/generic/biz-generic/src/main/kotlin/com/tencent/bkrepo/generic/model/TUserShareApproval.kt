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

package com.tencent.bkrepo.generic.model

import com.tencent.bkrepo.generic.model.TUserShareApproval.Companion.APPROVAL_ID_IDX
import com.tencent.bkrepo.generic.model.TUserShareApproval.Companion.APPROVAL_ID_IDX_DEF
import com.tencent.bkrepo.generic.model.TUserShareApproval.Companion.SHARE_ID_IDX
import com.tencent.bkrepo.generic.model.TUserShareApproval.Companion.SHARE_ID_IDX_DEF
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("user_share_approval")
@CompoundIndexes(
    CompoundIndex(name = SHARE_ID_IDX, def = SHARE_ID_IDX_DEF, unique = true, background = true),
    CompoundIndex(name = APPROVAL_ID_IDX, def = APPROVAL_ID_IDX_DEF, background = true),
)
data class TUserShareApproval(
    val id: String? = null,
    val shareId: String,
    val downloadUserId: String,
    val approvalId: String,
    val approvalTicketUrl: String,
    val createDate: LocalDateTime,
    val approved: Boolean,
    val approveUserId: String? = null,
    val approveDate: LocalDateTime? = null,
) {
    companion object {
        const val SHARE_ID_IDX = "shareId_downloadUserId_idx"
        const val APPROVAL_ID_IDX = "approvalId_downloadUserId_idx"
        const val SHARE_ID_IDX_DEF = "{'shareId': 1, 'downloadUserId': 1}"
        const val APPROVAL_ID_IDX_DEF = "{'approvalId': 1, 'downloadUserId': 1}"
    }
}
