package com.tencent.bkrepo.proxy.mongo

import com.mongodb.client.MongoClient
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.index.IndexOperations

class CustomMongoTemplate : MongoTemplate {
    constructor(mongoClient: MongoClient, databaseName: String) : super(
        SimpleMongoClientDatabaseFactory(
            mongoClient,
            databaseName
        ), null as MongoConverter?
    )


    constructor(mongoDbFactory: MongoDatabaseFactory) : super(mongoDbFactory)


    constructor(mongoDbFactory: MongoDatabaseFactory, mongoConverter: MongoConverter?) : super(
        mongoDbFactory,
        mongoConverter
    )

    override fun indexOps(collectionName: String): IndexOperations {
        return NoopIndexOperations()
    }

    override fun indexOps(collectionName: String, type: Class<*>?): IndexOperations {
        return NoopIndexOperations()
    }

    override fun indexOps(entityClass: Class<*>): IndexOperations {
        return NoopIndexOperations()
    }

}