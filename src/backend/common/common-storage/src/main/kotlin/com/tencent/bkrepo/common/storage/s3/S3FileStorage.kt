package com.tencent.bkrepo.common.storage.s3

import com.tencent.bkrepo.common.storage.AbstractFileStorage
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import java.io.InputStream

/**
 * S3文件存吃
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
class S3FileStorage(locateStrategy: LocateStrategy) : AbstractFileStorage(locateStrategy) {
    override fun store(path: String, filename: String, inputStream: InputStream) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun delete(path: String, filename: String) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun load(path: String, filename: String): InputStream {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun exist(path: String, filename: String): Boolean {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }
}
