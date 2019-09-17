package com.tencent.bkrepo.common.storage.local

import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import java.io.File
import java.io.InputStream

/**
 * CephFS文件存储
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
class LocalFileStorage(
        locateStrategy: LocateStrategy,
        defaultCredentials: LocalStorageCredentials
) : AbstractFileStorage<LocalStorageCredentials, Any>(locateStrategy, defaultCredentials) {

    override fun createClient(key: LocalStorageCredentials): Any {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun store(path: String, filename: String, inputStream: InputStream, client: Any) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun delete(path: String, filename: String, client: Any) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun load(path: String, filename: String, client: Any): InputStream {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun exist(path: String, filename: String, client: Any): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


/*    init {
        val localPath = File(directory)
        if(!localPath.exists()) {
            localPath.mkdirs()
        }
        assert(localPath.isDirectory) {"$directory is not a directory"}
    }*/


}
