package com.tencent.bkrepo.fs.server.handler.drive

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.TooManyRequestsException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.fs.server.bodyToArtifactFile
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveProperties
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
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drive 文件操作处理器
 *
 * 处理 Drive 文件的读取和写入请求
 */
@Component
class DriveOperationHandler(
    private val driveFileOperationService: DriveFileOperationService,
    private val driveNodeService: DriveNodeService,
    private val driveProperties: DriveProperties,
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
        val hasAcquired = tryAcquireWriteQuota()
        try {
            val user = ReactiveSecurityUtils.getUser()
            val artifactFile = request.bodyToArtifactFile()
            val blockRequest = DriveBlockWriteRequest(request)

            // 检查node是否已经被变更
            request.headers().firstHeader(HEADER_IF_MATCH)?.let { lastModifiedDate ->
                val node = driveNodeService.getNodeByIno(
                    blockRequest.projectId, blockRequest.repoName, blockRequest.ino
                )
                if (node.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME) != lastModifiedDate) {
                    throw ErrorCodeException(
                        status = HttpStatus.PRECONDITION_FAILED,
                        messageCode = CommonMessageCode.PRECONDITION_FAILED,
                        params = arrayOf(HEADER_IF_MATCH),
                    )
                }
            }

            // 写入数据
            val blockNode = driveFileOperationService.write(artifactFile, blockRequest, user)
            return ReactiveResponseBuilder.success(blockNode)
        } finally {
            if (hasAcquired) {
                ongoingWriteRequestCount.decrementAndGet()
            }
        }
    }

    private fun tryAcquireWriteQuota(): Boolean {
        val limit = driveProperties.writeRequestLimit
        if (limit <= 0) return false
        val currentCount = ongoingWriteRequestCount.incrementAndGet()
        if (currentCount > limit) {
            ongoingWriteRequestCount.decrementAndGet()
            throw TooManyRequestsException()
        }
        return true
    }

    companion object {
        private const val HEADER_IF_MATCH = "X-BKRepo-If-Match"
        private val logger = LoggerFactory.getLogger(DriveOperationHandler::class.java)
        private val ongoingWriteRequestCount = AtomicInteger(0)
    }
}
