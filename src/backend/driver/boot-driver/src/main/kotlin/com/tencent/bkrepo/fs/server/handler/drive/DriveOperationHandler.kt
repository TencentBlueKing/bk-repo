package com.tencent.bkrepo.fs.server.handler.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.fs.server.bodyToArtifactFile
import com.tencent.bkrepo.fs.server.request.drive.DriveBlockRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveBlockWriteRequest
import com.tencent.bkrepo.fs.server.resolveRange
import com.tencent.bkrepo.fs.server.service.drive.DriveFileOperationService
import com.tencent.bkrepo.fs.server.service.drive.DriveNodeService
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

/**
 * Drive 文件操作处理器
 *
 * 处理 Drive 文件的读取和写入请求
 */
@Component
class DriveOperationHandler(
    private val driveFileOperationService: DriveFileOperationService,
    private val driveNodeService: DriveNodeService,
) {

    /**
     * 读取 Drive 文件
     * 支持按范围读取，支持读取指定快照的数据
     */
    suspend fun read(request: ServerRequest): ServerResponse {
        with(DriveBlockRequest(request)) {
            val node = driveNodeService.getNodeByIno(projectId, repoName, ino)
            val range = try {
                request.resolveRange(node.size)
            } catch (e: IllegalArgumentException) {
                logger.info("read drive file[$projectId/$repoName/$ino] failed: ${e.message}")
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, HttpHeaders.RANGE)
            }
            val artifactInputStream = driveFileOperationService.read(node, range, snapSeq)
                ?: throw ArtifactNotFoundException("$projectId/$repoName/$ino")
            request.exchange().response.writeStream(artifactInputStream, range)
            return ok().buildAndAwait()
        }
    }

    /**
     * 写入 Drive 文件块
     * 写入的文件块立即可被读取
     */
    suspend fun write(request: ServerRequest): ServerResponse {
        val user = ReactiveSecurityUtils.getUser()
        val artifactFile = request.bodyToArtifactFile()
        val blockRequest = DriveBlockWriteRequest(request)
        val blockNode = driveFileOperationService.write(artifactFile, blockRequest, user)
        return ReactiveResponseBuilder.success(blockNode)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveOperationHandler::class.java)
    }
}
