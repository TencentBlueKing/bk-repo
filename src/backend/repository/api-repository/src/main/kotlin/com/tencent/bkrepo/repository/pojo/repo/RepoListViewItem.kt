package com.tencent.bkrepo.repository.pojo.repo

import com.tencent.bkrepo.common.artifact.path.PathUtils.UNIX_SEPARATOR
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class RepoListViewItem(
    val name: String,
    val createdBy: String,
    val lastModified: String,
    val category: String,
    val type: String,
    val public: String
) : Comparable<RepoListViewItem> {

    override fun compareTo(other: RepoListViewItem): Int {
        return this.name.compareTo(other.name)
    }

    companion object {
        private val formatters = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun from(repoInfo: RepositoryInfo): RepoListViewItem {
            with(repoInfo) {
                val normalizedName = name + UNIX_SEPARATOR
                val localDateTime = LocalDateTime.parse(lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
                val lastModified = formatters.format(localDateTime)
                return RepoListViewItem(normalizedName, lastModified, createdBy, category.name, type.name, public.toString())
            }
        }
    }
}
