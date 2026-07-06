package com.tencent.bkrepo.fs.server.handler.drive

import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.TYPE_FILE
import com.tencent.bkrepo.fs.server.bodyToArtifactFile
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import com.tencent.bkrepo.fs.server.readBody
import com.tencent.bkrepo.fs.server.request.NodeRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeUploadRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveTemporaryUrlCreateRequest
import com.tencent.bkrepo.fs.server.resolveBkRepoMetadata
import com.tencent.bkrepo.fs.server.resolveRange
import com.tencent.bkrepo.fs.server.service.drive.DriveFileOperationService
import com.tencent.bkrepo.fs.server.service.drive.DriveOperateLogService
import com.tencent.bkrepo.fs.server.service.drive.DrivePathResolveService
import com.tencent.bkrepo.fs.server.service.drive.DriveTemporaryAccessService
import com.tencent.bkrepo.fs.server.response.drive.toDriveNode
import com.tencent.bkrepo.fs.server.service.drive.DriveUploadService
import com.tencent.bkrepo.fs.server.utils.ReactiveResponseBuilder
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import com.tencent.bkrepo.fs.server.utils.ResponseWriter.writeStream
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.queryParamOrNull

@Component
class DriveTemporaryAccessHandler(
    private val driveTemporaryAccessService: DriveTemporaryAccessService,
    private val driveUploadService: DriveUploadService,
    private val drivePathResolveService: DrivePathResolveService,
    private val driveFileOperationService: DriveFileOperationService,
    private val driveOperateLogService: DriveOperateLogService,
) {
    suspend fun createToken(request: ServerRequest): ServerResponse {
        val userId = ReactiveSecurityUtils.getUser()
        val body = request.readBody(TemporaryTokenCreateRequest::class.java)
        val tokens = driveTemporaryAccessService.createToken(body, userId)
        return ReactiveResponseBuilder.success(tokens)
    }

    suspend fun createUrl(request: ServerRequest): ServerResponse {
        val userId = ReactiveSecurityUtils.getUser()
        val body = request.readBody(DriveTemporaryUrlCreateRequest::class.java)
        val urls = driveTemporaryAccessService.createUrl(body, userId)
        return ReactiveResponseBuilder.success(urls)
    }

    suspend fun upload(request: ServerRequest): ServerResponse {
        val clientAddress = ReactiveRequestContextHolder.getClientAddress()
        val token = request.queryParamOrNull(TOKEN_PARAM)
            ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, TOKEN_PARAM)
        val uploadRequest = DriveNodeUploadRequest(request)
        val tokenInfo = driveTemporaryAccessService.validateToken(
            token = token,
            projectId = uploadRequest.projectId,
            repoName = uploadRequest.repoName,
            fullPath = uploadRequest.fullPath,
            type = TokenType.UPLOAD,
            requestSnapSeq = null,
        )
        val userId = ReactiveSecurityUtils.getUser()
        driveUploadService.checkUploadTargetBeforeBody(uploadRequest, userId)
        val metadata = request.resolveBkRepoMetadata()
        val artifactFile = request.bodyToArtifactFile()
        val result = driveUploadService.uploadCompleteFile(uploadRequest, artifactFile, metadata, userId)
        driveTemporaryAccessService.decrementPermits(tokenInfo)
        driveOperateLogService.record(
            type = EventType.DRIVE_TEMPORARY_UPLOAD.name,
            userId = userId,
            clientAddress = clientAddress,
            projectId = uploadRequest.projectId,
            repoName = uploadRequest.repoName,
            resourceKey = uploadRequest.fullPath,
            description = mapOf(
                "fullPath" to uploadRequest.fullPath,
                "ino" to result.ino,
                "size" to result.size,
                "sha256" to artifactFile.getFileSha256(),
                "shareUserId" to tokenInfo.createdBy,
                "token" to maskToken(token),
            ),
        )
        return ReactiveResponseBuilder.success(result)
    }

    suspend fun download(request: ServerRequest): ServerResponse {
        val clientAddress = ReactiveRequestContextHolder.getClientAddress()
        val token = request.queryParamOrNull(TOKEN_PARAM)
            ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, TOKEN_PARAM)
        val nodeRequest = NodeRequest(request)
        val requestSnapSeq = request.queryParamOrNull(SNAP_SEQ_PARAM)?.toLongOrNull()
        val tokenInfo = driveTemporaryAccessService.validateToken(
            token = token,
            projectId = nodeRequest.projectId,
            repoName = nodeRequest.repoName,
            fullPath = nodeRequest.fullPath,
            type = TokenType.DOWNLOAD,
            requestSnapSeq = requestSnapSeq,
        )
        val userId = ReactiveSecurityUtils.getUser()
        val snapSeq = tokenInfo.snapSeq ?: requestSnapSeq
        val resourcePath = "${nodeRequest.projectId}/${nodeRequest.repoName}${nodeRequest.fullPath}"
        val driveNode = drivePathResolveService.resolveFileNode(
            nodeRequest.projectId,
            nodeRequest.repoName,
            nodeRequest.fullPath,
            snapSeq,
        )?.takeIf { it.type == TYPE_FILE }
            ?: throw ArtifactNotFoundException(resourcePath)
        val range = try {
            request.resolveRange(driveNode.size)
        } catch (e: IllegalArgumentException) {
            logger.info("temporary download drive file[$resourcePath] failed: ${e.message}")
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, HttpHeaders.RANGE)
        }
        val artifactInputStream = driveFileOperationService.read(driveNode.toDriveNode(), range, snapSeq)
            ?: throw ArtifactNotFoundException(resourcePath)
        request.exchange().response.writeStream(artifactInputStream, range)
        driveTemporaryAccessService.decrementPermits(tokenInfo)
        driveOperateLogService.record(
            type = EventType.DRIVE_TEMPORARY_DOWNLOAD.name,
            userId = userId,
            clientAddress = clientAddress,
            projectId = nodeRequest.projectId,
            repoName = nodeRequest.repoName,
            resourceKey = nodeRequest.fullPath,
            description = mapOf(
                "fullPath" to nodeRequest.fullPath,
                "ino" to driveNode.ino,
                "snapSeq" to snapSeq,
                "rangeStart" to range.start,
                "rangeEnd" to range.end,
                "shareUserId" to tokenInfo.createdBy,
                "token" to maskToken(token),
            ).filterValues { it != null } as Map<String, Any>,
        )
        return ok().buildAndAwait()
    }

    private fun maskToken(token: String): String {
        if (token.length <= 8) {
            return "****"
        }
        return "${token.take(4)}****${token.takeLast(4)}"
    }

    companion object {
        private const val TOKEN_PARAM = "token"
        private const val SNAP_SEQ_PARAM = "snapSeq"
        private val logger = LoggerFactory.getLogger(DriveTemporaryAccessHandler::class.java)
    }
}
