package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object NodeBaseServiceHelper {

    private val logger = LoggerFactory.getLogger(NodeBaseServiceHelper::class.java)
    const val TOPIC = "bkbase_bkrepo_artifact_node_created"

    fun checkNodeListOption(option: NodeListOption) {
        Preconditions.checkArgument(
            option.sortProperty.none { !TNode::class.java.declaredFields.map { f -> f.name }.contains(it) },
            "sortProperty",
        )
        Preconditions.checkArgument(
            option.direction.none { it != Sort.Direction.DESC.name && it != Sort.Direction.ASC.name },
            "direction",
        )
    }

    fun validateParameter(node: TNode): Boolean {
        if (node.folder) return false
        if (node.sha256.isNullOrBlank()) {
            logger.warn("Failed to change file reference, node[$node] sha256 is null or blank.")
            return false
        }
        return true
    }

    fun buildTNode(request: NodeCreateRequest, allowUserAddSystemMetadata: List<String>): TNode {
        with(request) {
            val normalizeFullPath = PathUtils.normalizeFullPath(fullPath)
            return TNode(
                projectId = projectId,
                repoName = repoName,
                path = PathUtils.resolveParent(normalizeFullPath),
                name = PathUtils.resolveName(normalizeFullPath),
                fullPath = normalizeFullPath,
                folder = folder,
                expireDate = if (folder) null else parseExpireDate(expires),
                size = if (folder) 0 else size ?: 0,
                sha256 = if (folder) null else sha256,
                md5 = if (folder) null else md5,
                nodeNum = null,
                metadata = MetadataUtils.compatibleConvertAndCheck(
                    metadata,
                    MetadataUtils.changeSystem(nodeMetadata, allowUserAddSystemMetadata),
                ),
                createdBy = createdBy ?: operator,
                createdDate = createdDate ?: LocalDateTime.now(),
                lastModifiedBy = createdBy ?: operator,
                lastModifiedDate = lastModifiedDate ?: LocalDateTime.now(),
                lastAccessDate = LocalDateTime.now(),
            )
        }
    }

    fun convert(tNode: TNode?): NodeInfo? {
        return tNode?.let {
            val metadata = MetadataUtils.toMap(it.metadata)
            NodeInfo(
                id = it.id,
                createdBy = it.createdBy,
                createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = it.lastModifiedBy,
                lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                projectId = it.projectId,
                repoName = it.repoName,
                folder = it.folder,
                path = it.path,
                name = it.name,
                fullPath = it.fullPath,
                size = if (it.size < 0L) 0L else it.size,
                nodeNum = it.nodeNum?.let { nodeNum ->
                    if (nodeNum < 0L) 0L else nodeNum
                },
                sha256 = it.sha256,
                md5 = it.md5,
                metadata = metadata,
                nodeMetadata = MetadataUtils.toList(it.metadata),
                copyFromCredentialsKey = it.copyFromCredentialsKey,
                copyIntoCredentialsKey = it.copyIntoCredentialsKey,
                deleted = it.deleted?.format(DateTimeFormatter.ISO_DATE_TIME),
                lastAccessDate = it.lastAccessDate?.format(DateTimeFormatter.ISO_DATE_TIME),
                clusterNames = it.clusterNames,
                archived = it.archived,
                compressed = it.compressed,
            )
        }
    }

    fun convertToDetail(tNode: TNode?): NodeDetail? {
        return convert(tNode)?.let { NodeDetail(it) }
    }

    /**
     * 根据有效天数，计算到期时间
     */
    fun parseExpireDate(expireDays: Long?): LocalDateTime? {
        return expireDays?.takeIf { it > 0 }?.run { LocalDateTime.now().plusDays(this) }
    }
}
