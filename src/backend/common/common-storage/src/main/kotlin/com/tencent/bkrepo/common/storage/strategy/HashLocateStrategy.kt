package com.tencent.bkrepo.common.storage.strategy

/**
 * 使用hash落地文件
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
class HashLocateStrategy : LocateStrategy {

    override fun locate(hash: String): String {

        val path = StringBuilder(FILE_SEPARATOR)
        for(i in 1..LOCATE_DEPTH) {
            path.append(hash.substring((i-1)*HASH_LENGTH, i*HASH_LENGTH)).append(FILE_SEPARATOR)
        }
        return path.toString()
    }

    companion object {

        private const val FILE_SEPARATOR = "/"
        private const val HASH_LENGTH = 2
        private const val LOCATE_DEPTH = 2
    }
}

