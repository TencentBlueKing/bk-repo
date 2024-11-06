package com.tencent.bkrepo.git.context

import com.tencent.bkrepo.git.internal.storage.DfsDataReader

class DfsDataReaders {
    private val readers = mutableMapOf<FileId, DfsDataReader>()

    fun getReader(id: FileId): DfsDataReader? {
        return readers[id]
    }

    fun putReader(id: FileId, dfsReader: DfsDataReader) {
        readers[id] = dfsReader
    }
}
