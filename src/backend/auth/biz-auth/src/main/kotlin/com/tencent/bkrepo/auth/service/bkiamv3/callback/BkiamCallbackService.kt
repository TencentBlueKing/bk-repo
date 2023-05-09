/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.service.bkiamv3.callback

import com.tencent.bk.sdk.iam.dto.callback.request.CallbackRequestDTO
import com.tencent.bk.sdk.iam.dto.callback.response.CallbackBaseResponseDTO
import com.tencent.bk.sdk.iam.service.TokenService
import com.tencent.bkrepo.auth.condition.MultipleAuthCondition
import com.tencent.bkrepo.auth.constant.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.auth.exception.AuthFailedException
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.StringPool
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service
import java.util.Base64

@Service
@Conditional(MultipleAuthCondition::class)
class BkiamCallbackService @Autowired constructor(
    private val tokenService: TokenService
) {
    @Value("\${auth.iam.callbackUser:}")
    private val callbackUser = ""

    private var bufferedToken = ""

    fun queryProject(token: String, request: CallbackRequestDTO): CallbackBaseResponseDTO? {
        logger.info("v3 queryProject, token: $token, request: $request")
        checkToken(token)
        return ResourceMappings.functionMap(ResourceType.PROJECT, request)
    }

    fun queryRepo(token: String, request: CallbackRequestDTO): CallbackBaseResponseDTO? {
        logger.info("v3 queryRepo, token: $token, request: $request")
        checkToken(token)
        return ResourceMappings.functionMap(ResourceType.REPO, request)
    }

    fun queryNode(token: String, request: CallbackRequestDTO): CallbackBaseResponseDTO? {
        logger.info("v3 queryNode, token: $token, request: $request")
        checkToken(token)
        return ResourceMappings.functionMap(ResourceType.NODE, request)
    }

    private fun checkToken(token: String) {
        val credentials = parseCredentials(token)
        val userName = credentials.first
        val password = credentials.second
        if (userName != callbackUser) {
            throw AuthFailedException("invalid iam user: $userName")
        }
        val tokenToCheck = password
        if (bufferedToken.isNotBlank() && bufferedToken == tokenToCheck) {
            return
        }
        bufferedToken = tokenService.token
        if (bufferedToken != tokenToCheck) {
            throw AuthFailedException("[$tokenToCheck] is not a valid credentials")
        }
    }

    private fun parseCredentials(token: String): Pair<String, String> {
        return try {
            val encodedCredentials = token.removePrefix(BASIC_AUTH_HEADER_PREFIX)
            val decodedToken = String(Base64.getDecoder().decode(encodedCredentials))
            val parts = decodedToken.split(StringPool.COLON)
            Pair(parts[0], parts[1])
        } catch (exception: IllegalArgumentException) {
            throw AuthFailedException("[$token] is not a valid token")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkiamCallbackService::class.java)
    }
}
