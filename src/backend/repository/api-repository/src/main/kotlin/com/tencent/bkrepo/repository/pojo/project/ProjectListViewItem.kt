package com.tencent.bkrepo.repository.pojo.project

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ProjectListViewItem(
    val name: String,
    val createdBy: String,
    val lastModified: String,
    val shardingIndex: String
) : Comparable<ProjectListViewItem> {

    override fun compareTo(other: ProjectListViewItem): Int {
        return this.name.compareTo(other.name)
    }

    companion object {
        private val formatters = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun from(projectInfo: ProjectInfo): ProjectListViewItem {
            with(projectInfo) {
                val normalizedName = name + PathUtils.SEPARATOR
                val localDateTime = LocalDateTime.parse(lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
                val lastModified = formatters.format(localDateTime)
                val shardingIndex = name.hashCode() and SHARDING_COUNT - 1
                return ProjectListViewItem(normalizedName, lastModified, createdBy, shardingIndex.toString())
            }
        }
    }
}
