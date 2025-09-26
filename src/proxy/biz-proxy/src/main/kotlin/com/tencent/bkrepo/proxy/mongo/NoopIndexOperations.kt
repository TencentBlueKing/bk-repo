package com.tencent.bkrepo.proxy.mongo

import com.tencent.bkrepo.common.api.constant.StringPool
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.data.mongodb.core.index.IndexInfo
import org.springframework.data.mongodb.core.index.IndexOperations
import org.springframework.data.mongodb.core.index.IndexOptions


class NoopIndexOperations : IndexOperations {
    override fun ensureIndex(indexDefinition: IndexDefinition): String {
        return StringPool.EMPTY
    }

    override fun alterIndex(name: String, options: IndexOptions) {
        return
    }

    override fun dropIndex(name: String) {
        return
    }

    override fun dropAllIndexes() {
        return
    }

    override fun getIndexInfo(): List<IndexInfo?> {
        return emptyList()
    }
}
