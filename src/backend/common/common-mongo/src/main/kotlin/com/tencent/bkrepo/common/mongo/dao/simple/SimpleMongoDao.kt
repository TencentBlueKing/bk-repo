package com.tencent.bkrepo.common.mongo.dao.simple

import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Query

abstract class SimpleMongoDao<E> : AbstractMongoDao<E>() {

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    override fun determineMongoTemplate(): MongoTemplate {
        return mongoTemplate
    }

    override fun determineCollectionName(entity: E): String {
        return collectionName
    }

    override fun determineCollectionName(query: Query): String {
        return collectionName
    }

    override fun determineCollectionName(aggregation: Aggregation): String {
        return collectionName
    }
}
