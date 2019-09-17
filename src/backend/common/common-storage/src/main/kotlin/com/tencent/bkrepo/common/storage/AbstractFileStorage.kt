package com.tencent.bkrepo.common.storage

import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import java.io.InputStream

/**
 * 文件存储抽象类
 * 对于上层来说，只需要提供文件hash值（如sha256），不需要关心文件如何落地与定位，统一由本层的LocateStrategy去判断，以达到更均衡的文件散列分布，同时避免文件冲突。
 * 通常来说上层会计算文件的hash(完整性校验等)，考虑到性能问题，因此hash统一由上层计算好传递进来。
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
abstract class AbstractFileStorage(
    private val locateStrategy: LocateStrategy
) : FileStorage {

    override fun store(hash: String, inputStream: InputStream) {
        val path = locateStrategy.locate(hash)
        store(path, hash, inputStream)
    }

    override fun delete(hash: String) {
        val path = locateStrategy.locate(hash)
        delete(path, hash)
    }

    override fun load(hash: String): InputStream {
        val path = locateStrategy.locate(hash)
        return load(path, hash)
    }

    override fun exist(hash: String): Boolean {
        val path = locateStrategy.locate(hash)
        return exist(path, hash)
    }

    protected abstract fun store(path: String, filename: String, inputStream: InputStream)
    protected abstract fun delete(path: String, filename: String)
    protected abstract fun load(path: String, filename: String): InputStream
    protected abstract fun exist(path: String, filename: String): Boolean
}
