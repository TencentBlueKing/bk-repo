package com.tencent.bkrepo.common.mongo.dao.util

import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.IndexDefinition

/**
 * mongo db 索引解析
 *
 * @author: carrypan
 * @date: 2019/11/7
 */
object MongoIndexResolver {

    fun resolveIndexFor(clazz: Class<*>): List<IndexDefinition> {
        val indexDefinitions = mutableListOf<IndexDefinition>()

        clazz.getAnnotation(CompoundIndexes::class.java)?.run {
            this.value.forEach { indexDefinitions.add(resolveCompoundIndexDefinition(it)) }
        }

        clazz.getAnnotation(CompoundIndex::class.java)?.run {
            indexDefinitions.add(resolveCompoundIndexDefinition(this))
        }

        return indexDefinitions
    }

    private fun resolveCompoundIndexDefinition(index: CompoundIndex): IndexDefinition {
        val indexDefinition = CompoundIndexDefinition(resolveCompoundIndexKeyFromStringDefinition(index.def))

        if (!index.useGeneratedName) {
            indexDefinition.named(index.name)
        }

        if (index.unique) {
            indexDefinition.unique()
        }

        if (index.sparse) {
            indexDefinition.sparse()
        }

        if (index.background) {
            indexDefinition.background()
        }

        return indexDefinition
    }

    private fun resolveCompoundIndexKeyFromStringDefinition(keyDefinitionString: String): org.bson.Document {
        if (keyDefinitionString.isBlank()) {
            throw InvalidDataAccessApiUsageException("Cannot create index on root level for empty keys.")
        }
        return org.bson.Document.parse(keyDefinitionString)
    }
}
