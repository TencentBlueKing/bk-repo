package com.tencent.bkrepo.preview.service.share

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.dao.drive.DriveNodeDao
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.common.metadata.util.drive.DriveNodePathHelper
import org.springframework.stereotype.Component

data class DriveShareNodeInfo(
    val node: TDriveNode,
    val fullPath: String,
)

/**
 * 同步解析 Drive 文件节点（供 preview 素材分享使用）。
 */
@Component
class DriveShareNodeResolver(
    private val driveNodeDao: DriveNodeDao,
) {

    fun resolveFileByIno(projectId: String, repoName: String, ino: Long): DriveShareNodeInfo? {
        val node = driveNodeDao.findCurrentByIno(projectId, repoName, ino)
            ?.takeIf { it.type == TDriveNode.TYPE_FILE }
            ?: return null
        val segments = mutableListOf(node.name)
        var parentIno = node.parent
        while (parentIno != null && parentIno != DriveNodePathHelper.ROOT_INO) {
            val parent = driveNodeDao.findCurrentByIno(projectId, repoName, parentIno) ?: return null
            segments.add(0, parent.name)
            parentIno = parent.parent
        }
        val fullPath = PathUtils.ROOT + segments.joinToString(PathUtils.UNIX_SEPARATOR.toString())
        return DriveShareNodeInfo(node = node, fullPath = fullPath)
    }

    fun metadataValue(node: TDriveNode, key: String): String? {
        return node.metadata
            ?.firstOrNull { it.key == key }
            ?.value
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val METADATA_AGENT_ID = "IMATE_AGENT_ID"
        const val METADATA_CONVERSATION_ID = "IMATE_CONVERSATION_ID"
        const val METADATA_ARTIFACT_NAME = "IMATE_ARTIFACT_NAME"
        const val METADATA_ARTIFACT_TYPE = "IMATE_ARTIFACT_TYPE"
    }
}
