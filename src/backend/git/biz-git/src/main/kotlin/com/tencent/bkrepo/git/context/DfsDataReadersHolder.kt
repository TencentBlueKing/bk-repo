package com.tencent.bkrepo.git.context

import com.tencent.bkrepo.common.api.thread.TransmittableThreadLocal

object DfsDataReadersHolder {
    private val dfsReaderHolder = TransmittableThreadLocal<DfsDataReaders>()

    fun setDfsReader(readers: DfsDataReaders) {
        dfsReaderHolder.set(readers)
    }

    fun reset() {
        dfsReaderHolder.remove()
    }

    fun getDfsReaders(): DfsDataReaders {
        return dfsReaderHolder.get()
    }
}
