package com.tencent.bkrepo.git.internal.storage

import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase
import org.eclipse.jgit.internal.storage.dfs.DfsPackDescription
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription
import org.eclipse.jgit.internal.storage.pack.PackExt
import kotlin.math.max

/**
 * pack文件描述符
 * */
class CodePackDescription(
    repoDesc: DfsRepositoryDescription,
    val packName: String,
    packSource: DfsObjDatabase.PackSource
) : DfsPackDescription(
    repoDesc,
    packName,
    packSource
) {
    var extensions: Int = 0
    var sizeMap: LongArray
    var blockSizeMap: LongArray

    init {
        val extCnt = PackExt.values().size
        sizeMap = LongArray(extCnt)
        blockSizeMap = LongArray(extCnt)
    }

    override fun setFileSize(ext: PackExt, bytes: Long): DfsPackDescription {
        val i = ext.position
        if (i >= sizeMap.size) {
            sizeMap = sizeMap.copyOf(i + 1)
        }
        sizeMap[i] = max(0, bytes)
        return this
    }

    override fun getFileSize(ext: PackExt): Long {
        val i = ext.position
        return if (i < sizeMap.size) sizeMap[i] else 0
    }

    override fun setBlockSize(ext: PackExt, blockSize: Int): DfsPackDescription {
        val i = ext.position
        if (i >= blockSizeMap.size) {
            blockSizeMap = blockSizeMap.copyOf(i + 1)
        }
        blockSizeMap[i] = 0.coerceAtLeast(blockSize).toLong()
        return this
    }

    override fun getBlockSize(ext: PackExt): Int {
        val i = ext.position
        return if (i < blockSizeMap.size) blockSizeMap[i].toInt() else 0
    }

    override fun getFileName(ext: PackExt): String {
        return this.packName + '.' + ext.extension
    }

    override fun addFileExt(ext: PackExt) {
        extensions = extensions or ext.bit
    }

    override fun hasFileExt(ext: PackExt): Boolean {
        return extensions and ext.bit != 0
    }
}
