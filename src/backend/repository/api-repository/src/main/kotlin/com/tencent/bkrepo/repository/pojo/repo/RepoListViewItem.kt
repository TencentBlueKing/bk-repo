package com.tencent.bkrepo.repository.pojo.repo

import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.util.NodeUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class RepoListViewItem(
    val name: String,
    val lastModified: String,
    val createdBy: String,
    val shardingIndex: String
) : Comparable<RepoListViewItem> {

    override fun compareTo(other: RepoListViewItem): Int {
        return this.name.compareTo(other.name)
    }

    companion object {
        private val formatters = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun from(repoInfo: RepositoryInfo): RepoListViewItem {
            with(repoInfo) {
                return from(name, lastModifiedDate, createdBy, projectId)
            }
        }

        fun from(projectInfo: ProjectInfo): RepoListViewItem {
            with(projectInfo) {
                return from(name, lastModifiedDate, createdBy, name)
            }
        }

        private fun from(name: String, lastModifiedDate: String, createdBy: String, shardingValue: String): RepoListViewItem {
            val normalizedName = name + NodeUtils.FILE_SEPARATOR
            val localDateTime = LocalDateTime.parse(lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
            val lastModified = formatters.format(localDateTime)
            val shardingIndex = shardingValue.hashCode() and SHARDING_COUNT - 1
            return RepoListViewItem(normalizedName, lastModified, createdBy, shardingIndex.toString())
        }
    }
}
