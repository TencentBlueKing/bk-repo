package com.tencent.bkrepo.docker.repomd

interface DirectoryItem {
    fun getRepoId(): String

    fun getPath(): String

    fun isFolder(): Boolean
}
