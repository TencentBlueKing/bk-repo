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

import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.api.util.UrlFormatter
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.DEFAULT_STORAGE_KEY
import com.tencent.bkrepo.common.artifact.constant.REPO_KEY
import com.tencent.bkrepo.common.artifact.event.ChunkArtifactTransferEvent
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.metrics.ChunkArtifactTransferMetrics
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.artifact.GenericChunkedArtifactInfo
import com.tencent.bkrepo.generic.config.GenericProperties
import com.tencent.bkrepo.generic.constant.CHUNKED_UPLOAD
import com.tencent.bkrepo.generic.constant.GenericMessageCode
import com.tencent.bkrepo.generic.constant.HEADER_UPLOAD_TYPE
import com.tencent.bkrepo.generic.extension.TemporaryUrlNotifyContext
import com.tencent.bkrepo.generic.extension.TemporaryUrlNotifyExtension
import com.tencent.bkrepo.generic.pojo.ChunkedResponseProperty
import com.tencent.bkrepo.generic.pojo.TemporaryAccessToken
import com.tencent.bkrepo.generic.pojo.TemporaryAccessUrl
import com.tencent.bkrepo.generic.pojo.TemporaryUrlCreateRequest
import com.tencent.bkrepo.generic.util.ChunkedRequestUtil.uploadResponse
import com.tencent.devops.plugin.api.PluginManager
import com.tencent.devops.plugin.api.applyExtension
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 临时访问服务
 */
@Service
class TemporaryAccessService(
    private val temporaryTokenClient: ServiceTemporaryTokenClient,
    private val repositoryService: RepositoryService,
    private val genericProperties: GenericProperties,
    private val pluginManager: PluginManager,
    private val deltaSyncService: DeltaSyncService,
    private val permissionManager: PermissionManager,
    private val storageService: StorageService,
) {

    /**
     * 上传
     */
    fun upload(artifactInfo: GenericArtifactInfo, file: ArtifactFile) {
        with(artifactInfo) {
            val repo = repositoryService.getRepoDetail(projectId, repoName)
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
            val repo = repositoryService.getRepoDetail(projectId, repoName)
                ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
            HttpContextHolder.getRequest().setAttribute(REPO_KEY, repo)
            val context = ArtifactDownloadContext(repo)
            ArtifactContextHolder.getRepository(repo.category).download(context)
        }
    }

    fun downloadByShare(userId: String, shareBy: String, artifactInfo: ArtifactInfo) {
        logger.info("share artifact[$artifactInfo] download user: $userId")
        checkAlphaApkDownloadUser(userId, artifactInfo, shareBy)
        with(artifactInfo) {
            val downloadUser = if (userId == ANONYMOUS_USER) shareBy else userId
            val repo = repositoryService.getRepoDetail(projectId, repoName)
                ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
            val context = ArtifactDownloadContext(repo = repo, userId = downloadUser)
            context.shareUserId = shareBy
            val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
            repository.download(context)
        }
    }

    /**
     * 加固签名的apk包，匿名下载时，使用分享人身份下载
     */
    private fun checkAlphaApkDownloadUser(userId: String, artifactInfo: ArtifactInfo, shareUserId: String) {
        val nodeDetail = ArtifactContextHolder.getNodeDetail(artifactInfo)
            ?: throw NodeNotFoundException(artifactInfo.getArtifactFullPath())
        val appStageKey = nodeDetail.metadata.keys.find { it.equals(BK_CI_APP_STAGE_KEY, true) }
            ?: return
        val alphaApk = nodeDetail.metadata[appStageKey]?.toString().equals(ALPHA, true)
        if (alphaApk && userId == ANONYMOUS_USER) {
            HttpContextHolder.getRequest().setAttribute(USER_KEY, shareUserId)
        }
    }

    /**
     * 根据[request]创建临时访问url
     * type必须指定具体的类型否则无法确定url
     * 创建出的url格式为$host/generic/temporary/$type/$project/$repo/$path?token=$token
     */
    fun createUrl(request: TemporaryUrlCreateRequest): List<TemporaryAccessUrl> {
        with(request) {
            Preconditions.checkArgument(type == TokenType.UPLOAD || type == TokenType.DOWNLOAD, "type")
            Preconditions.checkArgument(permits ?: Int.MAX_VALUE > 0, "permits")
            val temporaryTokenRequest = TemporaryTokenCreateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPathSet = fullPathSet,
                authorizedUserSet = authorizedUserSet,
                authorizedIpSet = authorizedIpSet,
                expireSeconds = expireSeconds,
                permits = permits,
                type = type,
            )
            val urlList = temporaryTokenClient.createToken(temporaryTokenRequest).data.orEmpty().map {
                TemporaryAccessUrl(
                    projectId = it.projectId,
                    repoName = it.repoName,
                    fullPath = it.fullPath,
                    url = generateAccessUrl(it, type, host),
                    authorizedUserList = it.authorizedUserList,
                    authorizedIpList = it.authorizedIpList,
                    expireDate = it.expireDate,
                    permits = it.permits,
                    type = it.type.name,
                )
            }
            if (needsNotify) {
                val context = TemporaryUrlNotifyContext(
                    userId = SecurityUtils.getUserId(),
                    urlList = urlList,
                )
                pluginManager.applyExtension<TemporaryUrlNotifyExtension> { notify(context) }
            }
            return urlList
        }
    }

    /**
     * 根据[request]创建临时访问token
     */
    fun createToken(request: TemporaryTokenCreateRequest): List<TemporaryAccessToken> {
        with(request) {
            Preconditions.checkArgument(permits ?: Int.MAX_VALUE > 0, "permits")
            return temporaryTokenClient.createToken(this).data.orEmpty().map {
                TemporaryAccessToken(
                    projectId = it.projectId,
                    repoName = it.repoName,
                    fullPath = it.fullPath,
                    token = it.token,
                    authorizedUserList = it.authorizedUserList,
                    authorizedIpList = it.authorizedIpList,
                    expireDate = it.expireDate,
                    permits = it.permits,
                    type = it.type.name,
                )
            }
        }
    }

    /**
     * 减少[tokenInfo]访问次数
     * 如果[tokenInfo]访问次数 <= 1，则直接删除
     */
    fun decrementPermits(tokenInfo: TemporaryTokenInfo) {
        if (tokenInfo.permits == null) {
            return
        }
        if (tokenInfo.permits!! <= 1) {
            temporaryTokenClient.deleteToken(tokenInfo.token)
        } else {
            temporaryTokenClient.decrementPermits(tokenInfo.token)
        }
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
        type: TokenType,
    ): TemporaryTokenInfo {
        val temporaryToken = checkToken(token)
        checkExpireTime(temporaryToken.expireDate)
        checkAccessType(temporaryToken.type, type)
        checkAccessResource(temporaryToken, artifactInfo)
        checkAuthorization(temporaryToken)
        checkAccessPermits(temporaryToken.permits)
        return temporaryToken
    }

    /**
     * 增量签名
     * */
    fun sign(artifactInfo: GenericArtifactInfo, md5: String?) {
        with(artifactInfo) {
            val repo = repositoryService.getRepoDetail(projectId, repoName)
                ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
            val request = HttpContextHolder.getRequest()
            request.setAttribute(REPO_KEY, repo)
            deltaSyncService.downloadSignFile(md5)
        }
    }

    /**
     * 合并
     * */
    fun patch(artifactInfo: GenericArtifactInfo, oldFilePath: String, deltaFile: ArtifactFile): SseEmitter {
        with(artifactInfo) {
            val repo = repositoryService.getRepoDetail(projectId, repoName)
                ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
            val request = HttpContextHolder.getRequest()
            request.setAttribute(REPO_KEY, repo)
            return deltaSyncService.patch(oldFilePath, deltaFile)
        }
    }

    /**
     * 上传sign file
     * */
    fun uploadSignFile(signFile: ArtifactFile, artifactInfo: GenericArtifactInfo, md5: String) {
        deltaSyncService.uploadSignFile(signFile, artifactInfo, md5)
    }


    fun getUuidForChunkedUpload(artifactInfo: GenericChunkedArtifactInfo, artifactFile: ArtifactFile) {
        with(artifactInfo) {
            val result = repositoryService.getRepoDetail(projectId, repoName)
                ?: throw RepoNotFoundException(repoName)
            val responseProperty = if (uuid.isNullOrEmpty()) {
                val uuidCreated = storageService.createAppendId(result.storageCredentials)
                ChunkedResponseProperty(
                    uuid = uuidCreated,
                    status = HttpStatus.ACCEPTED,
                    contentLength = 0
                )
            } else {
                val lengthOfAppendFile = try {
                    storageService.findLengthOfAppendFile(
                        uuid!!, result.storageCredentials
                    )
                } catch (ignore: StorageErrorException) {
                    throw BadRequestException(GenericMessageCode.CHUNKED_ARTIFACT_BROKEN, sha256.orEmpty())
                }
                ChunkedResponseProperty(
                    uuid = uuid,
                    status = HttpStatus.ACCEPTED,
                    contentLength = lengthOfAppendFile
                )
            }
            uploadResponse(
                responseProperty,
                HttpContextHolder.getResponse()
            )
        }
    }

    fun uploadArtifact(artifactInfo: GenericChunkedArtifactInfo, artifactFile: ArtifactFile) {
        val context = ArtifactUploadContext(artifactFile)
        val uploadType = HeaderUtils.getHeader(HEADER_UPLOAD_TYPE)
        if (uploadType.isNullOrEmpty() || uploadType != CHUNKED_UPLOAD)
            throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, "Missing expected chunked upload header!")
        ArtifactContextHolder.getRepository().upload(context)
    }

    fun reportChunkedMetrics(metrics: ChunkArtifactTransferMetrics) {
        if (metrics.success) {
            val repo = repositoryService.getRepoDetail(metrics.projectId, metrics.repoName) ?: return
            metrics.storage = repo.storageCredentials?.key ?: DEFAULT_STORAGE_KEY
            SpringContextUtils.publishEvent(ChunkArtifactTransferEvent(metrics))
        }
        logger.info(metrics.toJsonString().replace(System.lineSeparator(), ""))
    }


    /**
     * 根据token生成url
     */
    private fun generateAccessUrl(tokenInfo: TemporaryTokenInfo, tokenType: TokenType, host: String?): String {
        val urlHost = if (!host.isNullOrBlank()) host else genericProperties.domain
        val builder = StringBuilder(UrlFormatter.formatHost(urlHost))
        when (tokenType) {
            TokenType.DOWNLOAD -> builder.append(TEMPORARY_DOWNLOAD_ENDPOINT)
            TokenType.UPLOAD -> builder.append(TEMPORARY_UPLOAD_ENDPOINT)
            else -> builder.append(TEMPORARY_DOWNLOAD_ENDPOINT) // default use download
        }
        return builder.append(StringPool.SLASH)
            .append(tokenInfo.projectId)
            .append(StringPool.SLASH)
            .append(tokenInfo.repoName)
            .append(tokenInfo.fullPath)
            .append("?token=")
            .append(tokenInfo.token)
            .toString()
    }

    /**
     * 检查token并返回token信息
     */
    private fun checkToken(token: String): TemporaryTokenInfo {
        if (token.isBlank()) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, token)
        }
        return temporaryTokenClient.getTokenInfo(token).data
            ?: throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, token)
    }

    /**
     * 检查token是否过期
     */
    private fun checkExpireTime(expireDateString: String?) {
        expireDateString?.let {
            val expireDate = LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
            if (expireDate.isBefore(LocalDateTime.now())) {
                throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_EXPIRED)
            }
        }
    }

    /**
     * 检查访问次数
     */
    private fun checkAccessPermits(permits: Int?) {
        permits?.let {
            if (it <= 0) {
                throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_EXPIRED)
            }
        }
    }

    /**
     * 检查访问类型
     */
    private fun checkAccessType(grantedType: TokenType, accessType: TokenType) {
        if (grantedType != TokenType.ALL && grantedType != accessType) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, accessType)
        }
    }

    /**
     * 检查访问资源
     */
    private fun checkAccessResource(tokenInfo: TemporaryTokenInfo, artifactInfo: ArtifactInfo) {
        // 校验项目/仓库
        if (tokenInfo.projectId != artifactInfo.projectId || tokenInfo.repoName != artifactInfo.repoName) {
            throw ErrorCodeException(
                ArtifactMessageCode.TEMPORARY_TOKEN_INVALID,
                "${artifactInfo.projectId}/${artifactInfo.repoName}"
            )
        }
        // 校验路径
        if (!PathUtils.isSubPath(artifactInfo.getArtifactFullPath(), tokenInfo.fullPath)) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, artifactInfo.getArtifactFullPath())
        }
        // 校验创建人权限
        permissionManager.checkNodePermission(
            if (tokenInfo.type == TokenType.DOWNLOAD) PermissionAction.READ else PermissionAction.WRITE,
            artifactInfo.projectId,
            artifactInfo.repoName,
            artifactInfo.getArtifactFullPath(),
            userId = tokenInfo.createdBy,
        )
    }

    /**
     * 检查授权用户和ip
     */
    private fun checkAuthorization(tokenInfo: TemporaryTokenInfo) {
        // 检查用户授权
        // 获取经过认证的uid
        val authenticatedUid = SecurityUtils.getUserId()
        // 使用认证uid校验授权
        if (tokenInfo.authorizedUserList.isNotEmpty() && authenticatedUid !in tokenInfo.authorizedUserList) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, authenticatedUid)
        }
        // 获取需要审计的uid
        val auditedUid = if (SecurityUtils.isAnonymous()) {
            HttpContextHolder.getRequest().getHeader(AUTH_HEADER_UID) ?: tokenInfo.createdBy
        } else {
            authenticatedUid
        }
        // 设置审计uid到session中
        HttpContextHolder.getRequestOrNull()?.setAttribute(USER_KEY, auditedUid)
        // 校验ip授权
        val clientIp = HttpContextHolder.getClientAddress()
        if (tokenInfo.authorizedIpList.isNotEmpty() && clientIp !in tokenInfo.authorizedIpList) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, clientIp)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TemporaryAccessService::class.java)
        private const val TEMPORARY_DOWNLOAD_ENDPOINT = "/temporary/download"
        private const val TEMPORARY_UPLOAD_ENDPOINT = "/temporary/upload"
        private const val BK_CI_APP_STAGE_KEY = "BK-CI-APP-STAGE"
        private const val ALPHA = "Alpha"
    }
}
