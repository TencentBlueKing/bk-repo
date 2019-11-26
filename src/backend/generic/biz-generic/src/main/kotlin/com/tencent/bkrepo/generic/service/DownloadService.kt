package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.util.CredentialsUtils
import com.tencent.bkrepo.generic.constant.CONTENT_DISPOSITION_TEMPLATE
import com.tencent.bkrepo.generic.constant.DEFAULT_MIME_TYPE
import com.tencent.bkrepo.generic.constant.GenericMessageCode
import com.tencent.bkrepo.generic.constant.REPO_TYPE
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.util.NodeUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.server.MimeMappings
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile
import javax.servlet.http.HttpServletResponse

/**
 * 通用文件下载服务类
 *
 * @author: carrypan
 * @date: 2019-10-11
 */
@Service
class DownloadService @Autowired constructor(
    private val repositoryResource: RepositoryResource,
    private val nodeResource: NodeResource,
    private val fileStorage: FileStorage
) {
    //@Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    fun simpleDownload(userId: String, artifactInfo: ArtifactInfo) {
        val projectId = artifactInfo.projectId
        val repoName = artifactInfo.repoName
        val fullPath = artifactInfo.coordinate.fullPath

        // 查询repository
        val repository = repositoryResource.queryDetail(projectId, repoName, REPO_TYPE).data ?: run {
            logger.warn("User[$userId] simply download file [${artifactInfo.getUri()}] failed: $repoName not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
        }

        // 查询节点
        val node = nodeResource.queryDetail(projectId, repoName, fullPath).data ?: run {
            logger.warn("User[$userId] simply download file [${artifactInfo.getUri()}] failed: $fullPath not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)
        }

        // 如果为目录
        if (node.nodeInfo.folder) {
            logger.warn("User[$userId] simply download file [${artifactInfo.getUri()}] failed: $fullPath is a directory")
            throw ErrorCodeException(GenericMessageCode.DOWNLOAD_FOLDER_FORBIDDEN)
        }

        // 如果为分块文件
        if (node.isBlockFile()) {
            logger.warn("User[$userId] simply download file [${artifactInfo.getUri()}] failed: $fullPath is a block file")
            throw ErrorCodeException(GenericMessageCode.DOWNLOAD_BLOCK_FORBIDDEN)
        }

        // fileStorage
        val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
        val file = fileStorage.load(node.nodeInfo.sha256!!, storageCredentials) ?: run {
            logger.warn("User[$userId] simply download file [${artifactInfo.getUri()}] failed: file data not found")
            throw ErrorCodeException(GenericMessageCode.FILE_DATA_NOT_FOUND)
        }

        handleDownload(node.nodeInfo, file)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    fun blockDownload(userId: String, artifactInfo: ArtifactInfo, sequence: Int) {
        val projectId = artifactInfo.projectId
        val repoName = artifactInfo.repoName
        val fullPath = artifactInfo.coordinate.fullPath

        // 查询仓库
        val repository = repositoryResource.queryDetail(projectId, repoName, REPO_TYPE).data ?: run {
            logger.warn("User[$userId] block download file [${artifactInfo.getUri()}] failed: $repoName not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
        }
        // 查询节点
        val node = nodeResource.queryDetail(projectId, repoName, fullPath).data ?: run {
            logger.warn("User[$userId] block download file [${artifactInfo.getUri()}] failed: $fullPath not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)
        }
        if (node.isBlockFile()) {
            node.blockList!!.find { it.sequence == sequence }?.run {
                // fileStorage
                val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
                val file = fileStorage.load(this.sha256, storageCredentials) ?: run {
                    logger.warn("User[$userId] download block [${artifactInfo.getUri()}/$sequence] failed: file data not found")
                    throw ErrorCodeException(GenericMessageCode.FILE_DATA_NOT_FOUND)
                }
                handleDownload(node.nodeInfo, file)
            } ?: run {
                logger.warn("User[$userId] download block [${artifactInfo.getUri()}/$sequence] failed: file data not found")
                throw ErrorCodeException(GenericMessageCode.FILE_DATA_NOT_FOUND)
            }
        } else {
            logger.warn("User[$userId] download block [${artifactInfo.getUri()}/$sequence] failed: it is a simple file")
            throw ErrorCodeException(GenericMessageCode.DOWNLOAD_SIMPLE_FORBIDDEN)
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun getBlockList(userId: String, artifactInfo: ArtifactInfo): List<BlockInfo> {
        artifactInfo.run {
            // 查询节点
            val node = nodeResource.queryDetail(projectId, repoName, artifactInfo.coordinate.fullPath).data ?: run {
                logger.warn("User[$userId] query file info [${artifactInfo.getUri()}] failed: node does not exist")
                throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, artifactInfo.coordinate.fullPath)
            }

            // 如果为目录或简单上传的文件则返回空列表
            node.takeIf { node.isBlockFile() }?.run {
                return node.blockList!!.map { BlockInfo(size = it.size, sha256 = it.sha256, sequence = it.sequence) }
            } ?: return emptyList()
        }
    }

    private fun determineMediaType(name: String): String {
        return MimeMappings.DEFAULT.get(NodeUtils.getExtension(name)) ?: DEFAULT_MIME_TYPE
    }

    private fun handleDownload(nodeInfo: NodeInfo, file: File) {
        val request = HttpContextHolder.getRequest()
        val response = HttpContextHolder.getResponse()

        val fileLength = file.length()
        var readStart = 0L
        var readEnd = fileLength

        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, CONTENT_DISPOSITION_TEMPLATE.format(nodeInfo.name))
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes")

        request.getHeader("Range")?.run {
            parseContentRange(this, fileLength)?.run {
                start?.let { readStart = start }
                end?.let { readEnd = end + 1 }

                response.status = HttpServletResponse.SC_PARTIAL_CONTENT
                response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes $start-$end/$fileLength")
            } ?: run {
                response.status = HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE
                logger.warn("Range download failed: range is invalid")
                return
            }
        }

        response.setHeader(HttpHeaders.CONTENT_LENGTH, (readEnd - readStart).toString())
        response.contentType = determineMediaType(nodeInfo.name)

        RandomAccessFile(file, "r").use {
            it.seek(readStart)
            val out = BufferedOutputStream(response.outputStream)

            val bufferSize = 1024
            val buffer = ByteArray(bufferSize)

            var current = readStart
            while ((current + bufferSize) <= readEnd) {
                val readLength = it.read(buffer)
                current += readLength
                out.write(buffer, 0, readLength)
            }
            if (current < readEnd) {
                val readLength = it.read(buffer, 0, (readEnd - current).toInt())
                out.write(buffer, 0, readLength)
            }
            out.flush()
            response.flushBuffer()
        }
    }

    private fun parseContentRange(rangeHeader: String?, total: Long): Range? {
        return try {
            rangeHeader?.takeIf { it.isNotBlank() && it.contains("-") && it.contains("bytes=") }?.run {
                val items = rangeHeader.replace("bytes=", "").trim().split("-")
                if (items.size < 2) return null

                val left = if (items[0].isBlank()) null else items[0].toLong()
                val right = if (items[1].isBlank()) null else items[1].toLong()
                // check parameter
                if (left == null && right == null) return null
                if (left?.compareTo(right ?: total - 1) ?: -1 >= 0) return null
                if (right?.compareTo(total - 1) ?: -1 >= 0) return null
                if (right?.compareTo(0) ?: 1 <= 0) return null

                return when {
                    left == null -> Range(total - right!!, total - 1)
                    right == null -> Range(left, total - 1)
                    else -> Range(left, right)
                }
            }
        } catch (exception: Exception) {
            null
        }
    }

    data class Range(val start: Long?, val end: Long?)

    companion object {
        private val logger = LoggerFactory.getLogger(DownloadService::class.java)
    }
}
