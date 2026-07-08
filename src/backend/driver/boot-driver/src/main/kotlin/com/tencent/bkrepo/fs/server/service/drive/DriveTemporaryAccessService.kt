package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.api.util.UrlFormatter
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.client.RAuthClient
import com.tencent.bkrepo.common.metadata.service.project.RProjectService
import com.tencent.bkrepo.fs.server.RepositoryCache
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveProperties
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import com.tencent.bkrepo.fs.server.request.drive.DriveTemporaryUrlCreateRequest
import com.tencent.bkrepo.fs.server.response.drive.DriveTemporaryAccessToken
import com.tencent.bkrepo.fs.server.response.drive.DriveTemporaryAccessUrl
import com.tencent.bkrepo.fs.server.service.PermissionService
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata.Companion.KEY_SHARE_ENABLED
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class DriveTemporaryAccessService(
    private val rAuthClient: RAuthClient,
    private val permissionService: PermissionService,
    private val projectService: RProjectService,
    private val driveProperties: DriveProperties,
) {
    suspend fun createToken(request: TemporaryTokenCreateRequest, userId: String): List<DriveTemporaryAccessToken> {
        Preconditions.checkArgument(
            TokenType.entries.contains(request.type),
            "type",
        )
        validateCreateRequest(request, userId)
        val tokenRequest = request.copy(createdBy = userId)
        val tokens = rAuthClient.createTemporaryToken(tokenRequest)
            .awaitSingle()
            .data
            .orEmpty()
            .map { toAccessToken(it) }
        logger.info(
            "Create drive temporary token[${request.projectId}/${request.repoName}] count[${tokens.size}] by [$userId]"
        )
        return tokens
    }

    suspend fun createUrl(request: DriveTemporaryUrlCreateRequest, userId: String): List<DriveTemporaryAccessUrl> {
        with(request) {
            Preconditions.checkArgument(
                TokenType.entries.contains(request.type),
                "type",
            )
            val tokenRequest = TemporaryTokenCreateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPathSet = fullPathSet,
                authorizedUserSet = authorizedUserSet,
                authorizedIpSet = authorizedIpSet,
                expireSeconds = expireSeconds,
                permits = permits,
                type = type,
                snapSeq = snapSeq,
            )
            val tokens = createToken(tokenRequest, userId)
            return tokens.map { token ->
                DriveTemporaryAccessUrl(
                    projectId = token.projectId,
                    repoName = token.repoName,
                    fullPath = token.fullPath,
                    url = generateAccessUrl(token, type, host),
                    authorizedUserList = token.authorizedUserList,
                    authorizedIpList = token.authorizedIpList,
                    expireDate = token.expireDate,
                    permits = token.permits,
                    type = token.type,
                    snapSeq = token.snapSeq,
                )
            }
        }
    }

    suspend fun validateToken(
        token: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        type: TokenType,
        requestSnapSeq: Long?,
    ): TemporaryTokenInfo {
        val temporaryToken = checkToken(token)
        checkExpireTime(temporaryToken.expireDate)
        checkAccessType(temporaryToken.type, type)
        checkAccessResource(temporaryToken, projectId, repoName, fullPath, type)
        checkAuthorization(temporaryToken)
        checkAccessPermits(temporaryToken.permits)
        checkSnapSeq(temporaryToken.snapSeq, requestSnapSeq)
        return temporaryToken
    }

    suspend fun decrementPermits(tokenInfo: TemporaryTokenInfo) {
        val permits = tokenInfo.permits ?: return
        if (permits <= 1) {
            rAuthClient.deleteTemporaryToken(tokenInfo.token).awaitSingle()
        } else {
            rAuthClient.decrementTemporaryTokenPermits(tokenInfo.token).awaitSingle()
        }
    }

    private suspend fun validateCreateRequest(request: TemporaryTokenCreateRequest, userId: String) {
        Preconditions.checkArgument(request.permits == null || (request.permits ?: 0) > 0, "permits")
        checkProjectShareEnabled(request.projectId)
        checkDriveRepository(request.projectId, request.repoName)
        val permissionAction = when (request.type) {
            TokenType.DOWNLOAD -> PermissionAction.READ
            TokenType.UPLOAD -> PermissionAction.WRITE
            TokenType.ALL -> PermissionAction.WRITE
            TokenType.PREVIEW -> PermissionAction.READ
        }
        request.fullPathSet.forEach { path ->
            permissionService.checkNodePermissionOrThrow(
                projectId = request.projectId,
                repoName = request.repoName,
                fullPath = PathUtils.normalizeFullPath(path),
                action = permissionAction,
                uid = userId,
            )
        }
    }

    private suspend fun checkProjectShareEnabled(projectId: String) {
        val projectInfo = projectService.getProjectInfo(projectId)
            ?: throw ErrorCodeException(ArtifactMessageCode.PROJECT_NOT_FOUND, projectId)
        val shareEnabled = projectInfo.metadata.firstOrNull { it.key == KEY_SHARE_ENABLED }?.value as? Boolean ?: true
        if (!shareEnabled) {
            throw ErrorCodeException(ArtifactMessageCode.SHARE_DISABLED, projectId)
        }
    }

    private suspend fun checkDriveRepository(projectId: String, repoName: String) {
        val repo = RepositoryCache.getRepoDetail(projectId, repoName)
        if (repo.type != RepositoryType.DRIVE) {
            throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
        }
    }

    private suspend fun checkAccessResource(
        tokenInfo: TemporaryTokenInfo,
        projectId: String,
        repoName: String,
        fullPath: String,
        type: TokenType,
    ) {
        if (tokenInfo.projectId != projectId || tokenInfo.repoName != repoName) {
            throw ErrorCodeException(
                ArtifactMessageCode.TEMPORARY_TOKEN_INVALID,
                "$projectId/$repoName",
            )
        }
        val normalizedPath = PathUtils.normalizeFullPath(fullPath)
        if (!PathUtils.isSubPath(normalizedPath, tokenInfo.fullPath)) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, normalizedPath)
        }
        val permissionAction = when (type) {
            TokenType.DOWNLOAD -> PermissionAction.READ
            TokenType.UPLOAD -> PermissionAction.WRITE
            TokenType.ALL -> PermissionAction.WRITE
            TokenType.PREVIEW -> throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, type.name)
        }
        if (!permissionService.checkNodePermission(
                projectId = projectId,
                repoName = repoName,
                fullPath = normalizedPath,
                action = permissionAction,
                uid = tokenInfo.createdBy,
            )
        ) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, tokenInfo.createdBy)
        }
    }

    private suspend fun checkAuthorization(tokenInfo: TemporaryTokenInfo) {
        val authenticatedUid = ReactiveSecurityUtils.getUser()
        if (tokenInfo.authorizedUserList.isNotEmpty() && authenticatedUid !in tokenInfo.authorizedUserList) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, authenticatedUid)
        }
        val auditedUid = if (ReactiveSecurityUtils.isAnonymous()) {
            tokenInfo.createdBy
        } else {
            authenticatedUid
        }
        ReactiveRequestContextHolder.getWebExchange().attributes[USER_KEY] = auditedUid
        val clientIp = ReactiveRequestContextHolder.getClientAddress()
        if (tokenInfo.authorizedIpList.isNotEmpty() && clientIp !in tokenInfo.authorizedIpList) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, clientIp)
        }
    }

    private fun checkSnapSeq(tokenSnapSeq: Long?, requestSnapSeq: Long?) {
        when {
            tokenSnapSeq == null && requestSnapSeq != null -> {
                throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, "snapSeq")
            }
            tokenSnapSeq != null && requestSnapSeq != null && tokenSnapSeq != requestSnapSeq -> {
                throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, "snapSeq")
            }
        }
    }

    private suspend fun checkToken(token: String): TemporaryTokenInfo {
        if (token.isBlank()) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, token)
        }
        return rAuthClient.getTemporaryTokenInfo(token).awaitSingle().data
            ?: throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, token)
    }

    private fun checkExpireTime(expireDateString: String?) {
        expireDateString?.let {
            val expireDate = LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
            if (expireDate.isBefore(LocalDateTime.now())) {
                throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_EXPIRED)
            }
        }
    }

    private fun checkAccessPermits(permits: Int?) {
        permits?.let {
            if (it <= 0) {
                throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_EXPIRED)
            }
        }
    }

    private fun checkAccessType(grantedType: TokenType, accessType: TokenType) {
        if (grantedType != TokenType.ALL && grantedType != accessType) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, accessType.name)
        }
    }

    private fun generateAccessUrl(token: DriveTemporaryAccessToken, tokenType: TokenType, host: String?): String {
        val urlHost = host?.takeIf { it.isNotBlank() } ?: driveProperties.domain
        val endpoint = when (tokenType) {
            TokenType.DOWNLOAD -> TEMPORARY_DOWNLOAD_ENDPOINT
            TokenType.UPLOAD -> TEMPORARY_UPLOAD_ENDPOINT
            TokenType.ALL -> TEMPORARY_DOWNLOAD_ENDPOINT
            TokenType.PREVIEW -> TEMPORARY_DOWNLOAD_ENDPOINT
        }
        if (tokenType == TokenType.PREVIEW) {
            return "$urlHost/ui/${token.projectId}/filePreview/local/0/${token.repoName}${token.fullPath}"
        }
        val builder = StringBuilder(UrlFormatter.formatHost(urlHost))
            .append("/fs-server")
            .append(endpoint)
            .append(StringPool.SLASH)
            .append(token.projectId)
            .append(StringPool.SLASH)
            .append(token.repoName)
            .append(token.fullPath)
            .append("?token=")
            .append(token.token)
        token.snapSeq?.let { builder.append("&snapSeq=").append(it) }
        return builder.toString()
    }

    private fun toAccessToken(tokenInfo: TemporaryTokenInfo): DriveTemporaryAccessToken {
        return DriveTemporaryAccessToken(
            projectId = tokenInfo.projectId,
            repoName = tokenInfo.repoName,
            fullPath = tokenInfo.fullPath,
            token = tokenInfo.token,
            authorizedUserList = tokenInfo.authorizedUserList,
            authorizedIpList = tokenInfo.authorizedIpList,
            expireDate = tokenInfo.expireDate,
            permits = tokenInfo.permits,
            type = tokenInfo.type.name,
            snapSeq = tokenInfo.snapSeq,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveTemporaryAccessService::class.java)
        private const val TEMPORARY_DOWNLOAD_ENDPOINT = "/drive/temporary/download"
        private const val TEMPORARY_UPLOAD_ENDPOINT = "/drive/temporary/upload"
    }
}
