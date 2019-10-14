package com.tencent.bkrepo.registry.repomd

abstract class Artifact : DirectoryItem {

    abstract fun getMd5(): String

    abstract fun getSha1(): String

    abstract fun getSha256(): String

    abstract fun getLength(): Long

    abstract fun getLastModified(): Long

    abstract fun getName(): String

    override fun isFolder(): Boolean {
        return false
    }
}
