package com.tencent.bkrepo.common.storage.filesystem.cleanup

import java.io.File

class CompositeFileExpireResolver(val list: List<FileExpireResolver>) : FileExpireResolver {
    override fun isExpired(file: File): Boolean {
        return list.firstOrNull { !it.isExpired(file) } == null
    }
}
