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

package com.tencent.bkrepo.auth.service.impl

import com.tencent.bkrepo.auth.model.TTemporaryToken
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.auth.repository.TemporaryTokenRepository
import com.tencent.bkrepo.auth.service.TemporaryTokenService
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.DefaultCondition
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 临时token服务实现类
 */
@Service("authTemporaryTokenServiceImpl")
@Conditional(DefaultCondition::class)
class TemporaryTokenServiceImpl(
    private val temporaryTokenRepository: TemporaryTokenRepository
) : TemporaryTokenService {

    override fun createToken(request: TemporaryTokenCreateRequest): List<TemporaryTokenInfo> {
        with(request) {
            return validateAndNormalize(this).map {
                val temporaryToken = TTemporaryToken(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = it,
                    expireDate = computeExpireDate(request.expireSeconds),
                    authorizedUserList = request.authorizedUserSet,
                    authorizedIpList = request.authorizedIpSet,
                    token = generateToken(),
                    permits = permits,
                    type = type,
                    createdBy = SecurityUtils.getUserId(),
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = SecurityUtils.getUserId(),
                    lastModifiedDate = LocalDateTime.now()
                )
                temporaryTokenRepository.save(temporaryToken)
                logger.info("Create share record[$temporaryToken] success.")
                convert(temporaryToken)
            }
        }
    }

    override fun getTokenInfo(token: String): TemporaryTokenInfo? {
        val temporaryToken = temporaryTokenRepository.findByToken(token) ?: return null
        return convert(temporaryToken)
    }

    override fun deleteToken(token: String) {
        temporaryTokenRepository.deleteByToken(token)
        logger.info("Delete temporary token[$token] success.")
    }

    override fun decrementPermits(token: String) {
        temporaryTokenRepository.decrementPermits(token)
        logger.info("Decrement permits of token[$token] success.")
    }

    /**
     * 验证数据格式， 格式化fullPath
     */
    private fun validateAndNormalize(request: TemporaryTokenCreateRequest): List<String> {
        with(request) {
            Preconditions.checkArgument(permits == null || permits!! > 0, "permits")
            return fullPathSet.map { PathUtils.normalizeFullPath(it) }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TemporaryTokenServiceImpl::class.java)

        private fun generateToken(): String {
            return UUID.randomUUID().toString().replace(StringPool.DASH, StringPool.EMPTY).toLowerCase()
        }

        private fun computeExpireDate(expireSeconds: Long?): LocalDateTime? {
            return if (expireSeconds == null || expireSeconds <= 0) null
            else LocalDateTime.now().plusSeconds(expireSeconds)
        }

        private fun convert(tTemporaryToken: TTemporaryToken): TemporaryTokenInfo {
            return tTemporaryToken.let {
                TemporaryTokenInfo(
                    fullPath = it.fullPath,
                    repoName = it.repoName,
                    projectId = it.projectId,
                    token = it.token,
                    authorizedUserList = it.authorizedUserList,
                    authorizedIpList = it.authorizedIpList,
                    expireDate = it.expireDate?.format(DateTimeFormatter.ISO_DATE_TIME),
                    type = it.type,
                    permits = it.permits,
                    createdBy = it.createdBy
                )
            }
        }
    }
}
