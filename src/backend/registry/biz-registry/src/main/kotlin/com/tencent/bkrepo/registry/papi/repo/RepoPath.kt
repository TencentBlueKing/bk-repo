package com.tencent.bkrepo.registry.papi.repo

import com.tencent.bkrepo.registry.common.Info

interface RepoPath : Info {

    abstract fun getRepoKey(): String

    abstract fun getPath(): String

    abstract fun getId(): String

    abstract fun toPath(): String

    abstract fun getName(): String

    abstract fun getParent(): RepoPath?

    abstract fun isRoot(): Boolean

    abstract fun isFile(): Boolean

    abstract fun isFolder(): Boolean

    companion object {
        val REPO_PATH_SEP = ':'
        val ARCHIVE_SEP = '!'
        val PATH_SEPARATOR = '/'
        val REMOTE_CACHE_SUFFIX = "-cache"
    }
}
