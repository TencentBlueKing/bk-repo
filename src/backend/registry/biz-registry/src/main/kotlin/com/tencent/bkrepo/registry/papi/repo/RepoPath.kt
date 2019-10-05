package com.tencent.bkrepo.registry.papi.repo

import com.tencent.bkrepo.registry.common.Info

interface RepoPath : Info {

    val repoKey: String

    fun getPath(): String

    val id: String

    val name: String

    val parent: RepoPath?

    val isRoot: Boolean

    val isFile: Boolean

    val isFolder: Boolean

    fun toPath(): String

    companion object {
        val REPO_PATH_SEP = ':'
        val ARCHIVE_SEP = '!'
        val PATH_SEPARATOR = '/'
        val REMOTE_CACHE_SUFFIX = "-cache"
    }
}
