package com.tencent.bkrepo.common.storage.core.locator

/**
 * 使用hash落地文件
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
class HashFileLocator : FileLocator {

    override fun locate(digest: String): String {
        val path = StringBuilder(FILE_SEPARATOR)
        for (i in 1..LOCATE_DEPTH) {
            path.append(digest.substring((i - 1) * HASH_LENGTH, i * HASH_LENGTH)).append(
                FILE_SEPARATOR
            )
        }
        return path.toString()
    }

    companion object {
        /**
         * 文件分隔符
         */
        private const val FILE_SEPARATOR = "/"
        /**
         * hash长度
         */
        private const val HASH_LENGTH = 2
        /**
         * hash深度
         */
        private const val LOCATE_DEPTH = 2
    }
}
