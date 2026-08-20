package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveProperties
import com.tencent.bkrepo.fs.server.utils.DriveServiceUtils
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 生成 Drive 节点预览 URL（按调用方 type 区分 Agent / Client）
 */
@Service
class DriveNodePreviewUrlService(
    private val drivePathResolveService: DrivePathResolveService,
    private val driveProperties: DriveProperties,
) {

    suspend fun buildPreviewUrl(
        projectId: String,
        repoName: String,
        ino: Long,
        type: String?,
    ): String {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        val pathInfo = drivePathResolveService.resolveFilePathInfoByIno(projectId, repoName, ino)
            ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, ino)
        if (type == TYPE_IMATE_AGENT) {
            return buildImateAgentUrl(ino, pathInfo)
        }
        val domain = driveProperties.domain.trim()
        if (domain.isBlank()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "domain")
        }
        return "$domain/ui/$projectId$FILE_PREVIEW_PATH_PREFIX$repoName${pathInfo.fullPath}"
    }

    private fun buildImateAgentUrl(ino: Long, pathInfo: DriveFilePathInfo): String {
        val fileName = PathUtils.resolveName(pathInfo.fullPath).ifBlank { ino.toString() }
        val displayName = metadataValue(pathInfo.node, METADATA_ARTIFACT_NAME) ?: fileName
        val artifactType = metadataValue(pathInfo.node, METADATA_ARTIFACT_TYPE)
            ?.takeIf { it in KNOWN_ARTIFACT_TYPES }
            ?: TYPE_OTHER
        val encodedName = URLEncoder.encode(displayName, StandardCharsets.UTF_8)
            .replace("+", "%20")
        return "$IMATE_AGENT_SCHEME$ino?name=$encodedName&type=$artifactType"
    }

    private fun metadataValue(node: TDriveNode, key: String): String? {
        return node.metadata
            ?.firstOrNull { it.key == key }
            ?.value
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val TYPE_IMATE_AGENT = "IMATE_AGENT"
        const val TYPE_IMATE_CLIENT = "IMATE_CLIENT"
        const val IMATE_AGENT_SCHEME = "imate_artifact://"
        const val METADATA_ARTIFACT_NAME = "IMATE_ARTIFACT_NAME"
        const val METADATA_ARTIFACT_TYPE = "IMATE_ARTIFACT_TYPE"
        private const val TYPE_OTHER = "other"
        private const val FILE_PREVIEW_PATH_PREFIX = "/filePreview/local/0/"
        private val KNOWN_ARTIFACT_TYPES = setOf(
            "image", "pdf", "html", "code", "table", "slides", "markdown", "video", "audio", "other",
        )
    }
}
