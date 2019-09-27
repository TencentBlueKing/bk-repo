package com.tencent.bkrepo.registry.repomd

interface DirectoryItem {
    fun getRepoId(): String

    fun getPath(): String

    fun isFolder(): Boolean
}
