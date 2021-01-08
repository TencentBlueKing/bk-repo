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

package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.TemporaryTokenClient
import com.tencent.bkrepo.repository.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.repository.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.repository.pojo.token.TokenType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 临时访问服务
 */
@Service
class TemporaryAccessService(
    private val temporaryTokenClient: TemporaryTokenClient,
    private val repositoryClient: RepositoryClient
) {

    /**
     * 上传
     */
    fun upload(artifactInfo: GenericArtifactInfo, file: ArtifactFile) {
        with(artifactInfo) {
            val repo = repositoryClient.getRepoDetail(projectId, repoName).data
                ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
            val context = ArtifactUploadContext(repo, file)
            ArtifactContextHolder.getRepository(repo.category).upload(context)
        }
    }

    /**
     * 下载
     */
    fun download(artifactInfo: GenericArtifactInfo) {
        with(artifactInfo) {
            val repo = repositoryClient.getRepoDetail(projectId, repoName).data
                ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
            val context = ArtifactDownloadContext(repo)
            ArtifactContextHolder.getRepository(repo.category).download(context)
        }
    }

    /**
     * 根据[request]创建临时token
     */
    fun createToken(request: TemporaryTokenCreateRequest): Response<List<TemporaryTokenInfo>> {
        return temporaryTokenClient.createToken(request)
    }

    /**
     * 让[token]失效
     */
    fun invalidateToken(token: String) {
        temporaryTokenClient.deleteToken(token)
        logger.info("Invalidate token[$token]")
    }

    /**
     * 校验[token]是否有效
     * @param token 待校验token
     * @param artifactInfo 访问构件信息
     * @param type 访问类型
     */
    fun validateToken(
        token: String,
        artifactInfo: ArtifactInfo,
        type: TokenType
    ): TemporaryTokenInfo {
        if (token.isBlank()) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID)
        }

        // 查询token
        val temporaryToken = temporaryTokenClient.getTokenInfo(token).data
            ?: throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID)
        // 校验过期时间
        temporaryToken.expireDate?.let {
            val expireDate = LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
            if (expireDate.isBefore(LocalDateTime.now())) {
                throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_EXPIRED)
            }
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
        val userId = SecurityUtils.getUserId()
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
        private val logger = LoggerFactory.getLogger(TemporaryAccessService::class.java)
    }
}
