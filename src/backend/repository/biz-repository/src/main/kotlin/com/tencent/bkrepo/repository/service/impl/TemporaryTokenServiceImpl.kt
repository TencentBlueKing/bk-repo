/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.dao.TemporaryTokenDao
import com.tencent.bkrepo.repository.model.TShareRecord
import com.tencent.bkrepo.repository.model.TTemporaryToken
import com.tencent.bkrepo.repository.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.repository.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.repository.pojo.token.TokenType
import com.tencent.bkrepo.repository.service.NodeService
import com.tencent.bkrepo.repository.service.TemporaryTokenService
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 临时token服务实现类
 */
@Service
class TemporaryTokenServiceImpl(
    private val temporaryTokenDao: TemporaryTokenDao,
    private val nodeService: NodeService,
    private val mongoTemplate: MongoTemplate
) : TemporaryTokenService, ArtifactService() {

    override fun createToken(request: TemporaryTokenCreateRequest): List<TemporaryTokenInfo> {
        with(request) {
            return formatAndCheckFullPath(projectId, repoName, fullPathSet).map {
                val temporaryToken = TTemporaryToken(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = it,
                    expireDate = computeExpireDate(request.expireSeconds),
                    authorizedUserList = request.authorizedUserSet,
                    authorizedIpList = request.authorizedIpSet,
                    token = generateToken(),
                    disposable = disposable,
                    type = type,
                    createdBy = SecurityUtils.getUserId(),
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = SecurityUtils.getUserId(),
                    lastModifiedDate = LocalDateTime.now()
                )
                temporaryTokenDao.save(temporaryToken)
                logger.info("Create share record[$temporaryToken] success.")
                convert(temporaryToken)
            }
        }
    }

    override fun getTokenInfo(token: String): TemporaryTokenInfo? {
        val temporaryToken = temporaryTokenDao.findByToken(token) ?: return null
        return convert(temporaryToken)
    }

    override fun deleteToken(token: String) {
        temporaryTokenDao.deleteByToken(token)
        logger.info("Delete temporary token[$token] success.")
    }

    /**
     * 格式化[fullPathSet]并校验节点是否存在
     */
    private fun formatAndCheckFullPath(projectId: String, repoName: String, fullPathSet: Set<String>): List<String> {
        return fullPathSet.map {
            val artifactInfo = DefaultArtifactInfo(projectId, repoName, it)
            if (!nodeService.checkExist(artifactInfo)) {
                throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, artifactInfo.getArtifactFullPath())
            }
            artifactInfo.getArtifactFullPath()
        }
    }

    /**
     * 校验[token]是否有效
     * @param token 待校验token
     * @param userId 访问用户id
     * @param artifactInfo 访问构件信息
     * @param type 访问类型
     */
    private fun findAndCheckToken(
        token: String,
        userId: String,
        artifactInfo: ArtifactInfo,
        type: TokenType
    ): TTemporaryToken {
        val query = Query.query(where(TShareRecord::token).isEqualTo(token))
        val temporaryToken = mongoTemplate.findOne(query, TTemporaryToken::class.java)
            ?: throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID)
        // 校验过期时间
        if (temporaryToken.expireDate?.isBefore(LocalDateTime.now()) == true) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_EXPIRED)
        }
        // 校验类型
        if (temporaryToken.type != type) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID)
        }
        // 校验项目
        if (temporaryToken.projectId != artifactInfo.projectId) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID)
        }
        // 校验仓库
        if (temporaryToken.repoName != artifactInfo.repoName) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID)
        }
        // 校验路径
        if (!PathUtils.isSubPath(artifactInfo.getArtifactFullPath(), temporaryToken.fullPath)) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID)
        }
        // 校验用户
        if (temporaryToken.authorizedUserList.isNotEmpty() && userId !in temporaryToken.authorizedUserList) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID)
        }
        // 校验ip
        val clientIp = HttpContextHolder.getClientAddress()
        if (temporaryToken.authorizedIpList.isNotEmpty() && clientIp !in temporaryToken.authorizedIpList) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID)
        }
        return temporaryToken
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
                    disposable = it.disposable
                )
            }
        }
    }
}
