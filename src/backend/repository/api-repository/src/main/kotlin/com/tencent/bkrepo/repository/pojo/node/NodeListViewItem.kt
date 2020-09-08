package com.tencent.bkrepo.repository.pojo.node

import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.path.PathUtils.UNIX_SEPARATOR
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 用于浏览器列表查看节点信息
 */
data class NodeListViewItem(
    val name: String,
    val lastModified: String,
    val createdBy: String,
    val size: String,
    val folder: Boolean
) : Comparable<NodeListViewItem> {

    override fun compareTo(other: NodeListViewItem): Int {
        return if (this.folder && !other.folder) -1
        else if (!this.folder && other.folder) 1
        else this.name.compareTo(other.name)
    }

    companion object {
        private val formatters = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        fun from(nodeInfo: NodeInfo): NodeListViewItem {
            val normalizedName = if (nodeInfo.folder) nodeInfo.name + UNIX_SEPARATOR else nodeInfo.name
            val normalizedSize = if (nodeInfo.folder) "-" else HumanReadable.size(nodeInfo.size)
            val localDateTime = LocalDateTime.parse(nodeInfo.lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
            val lastModified = formatters.format(localDateTime)
            return NodeListViewItem(normalizedName, lastModified, nodeInfo.createdBy, normalizedSize, nodeInfo.folder)
        }
    }
}
