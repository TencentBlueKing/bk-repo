package com.tencent.bkrepo.common.storage.cephfs

import com.tencent.bkrepo.common.storage.AbstractFileStorage
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import java.io.InputStream

/**
 * CephFS文件存储
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
class CephFileStorage(locateStrategy: LocateStrategy) : AbstractFileStorage(locateStrategy) {
    override fun store(filename: String, path: String, inputStream: InputStream) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun delete(filename: String, path: String) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun load(filename: String, path: String): InputStream {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun exist(filename: String, path: String): Boolean {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }
}
