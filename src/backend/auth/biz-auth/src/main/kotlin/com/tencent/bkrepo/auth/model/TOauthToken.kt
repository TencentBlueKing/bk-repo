/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.auth.model

import com.tencent.bkrepo.auth.model.TOauthToken.Companion.ACCESS_TOKEN_IDX
import com.tencent.bkrepo.auth.model.TOauthToken.Companion.ACCESS_TOKEN_IDX_DEF
import com.tencent.bkrepo.auth.model.TOauthToken.Companion.ACCOUNT_ID_ACCESS_TOKEN_IDX
import com.tencent.bkrepo.auth.model.TOauthToken.Companion.ACCOUNT_ID_ACCESS_TOKEN_IDX_DEF
import com.tencent.bkrepo.auth.model.TOauthToken.Companion.ACCOUNT_ID_USER_ID_IDX
import com.tencent.bkrepo.auth.model.TOauthToken.Companion.ACCOUNT_ID_USER_ID_IDX_DEF
import com.tencent.bkrepo.auth.model.TOauthToken.Companion.USER_IDX
import com.tencent.bkrepo.auth.model.TOauthToken.Companion.USER_IDX_DEF
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.oauth.IdToken
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("oauth_token")
@CompoundIndexes(
    CompoundIndex(name = ACCESS_TOKEN_IDX, def = ACCESS_TOKEN_IDX_DEF, background = true),
    CompoundIndex(name = USER_IDX, def = USER_IDX_DEF, background = true),
    CompoundIndex(name = ACCOUNT_ID_ACCESS_TOKEN_IDX, def = ACCOUNT_ID_ACCESS_TOKEN_IDX_DEF, background = true),
    CompoundIndex(name = ACCOUNT_ID_USER_ID_IDX, def = ACCOUNT_ID_USER_ID_IDX_DEF, background = true),
)data class TOauthToken(
    val id: String? = null,
    var accessToken: String,
    var refreshToken: String?,
    val expireSeconds: Long?,
    val type: String,
    val accountId: String,
    var userId: String,
    var scope: Set<ResourceType>?,
    var issuedAt: Instant,
    var idToken: IdToken?
) {
    companion object {
        const val ACCESS_TOKEN_IDX = "access_token"
        const val ACCESS_TOKEN_IDX_DEF = "{'accessToken': 1}"
        const val USER_IDX = "user_id"
        const val USER_IDX_DEF = "{'userId': 1}"
        const val ACCOUNT_ID_ACCESS_TOKEN_IDX = "account_id_access_token"
        const val ACCOUNT_ID_ACCESS_TOKEN_IDX_DEF = "{'accountId': 1, 'access_token': 1}"
        const val ACCOUNT_ID_USER_ID_IDX = "account_id_user_id"
        const val ACCOUNT_ID_USER_ID_IDX_DEF = "{'accountId': 1, 'userId': 1}"
    }
}
