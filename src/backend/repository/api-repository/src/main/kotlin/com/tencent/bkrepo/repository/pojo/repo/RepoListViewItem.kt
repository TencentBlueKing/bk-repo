package com.tencent.bkrepo.repository.pojo.repo

import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class RepoListViewItem(
    val name: String,
    val lastModifiedDate: String,
    val createdBy: String
) : Comparable<RepoListViewItem> {

    override fun compareTo(other: RepoListViewItem): Int {
        return this.name.compareTo(other.name)
    }

    companion object {
        private val formatters = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun from(repoInfo: RepositoryInfo): RepoListViewItem {
            val name = repoInfo.name
            val localDateTime = LocalDateTime.parse(repoInfo.lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
            val lastModifiedDate = formatters.format(localDateTime)
            return RepoListViewItem(name, lastModifiedDate, repoInfo.createdBy)
        }

        fun from(projectInfo: ProjectInfo): RepoListViewItem {
            val name = projectInfo.name
            val localDateTime = LocalDateTime.parse(projectInfo.lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
            val lastModifiedDate = formatters.format(localDateTime)
            return RepoListViewItem(name, lastModifiedDate, projectInfo.createdBy)
        }
    }
}
