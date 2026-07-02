package com.tencent.bkrepo.common.metadata.dao.drive

import com.tencent.bkrepo.common.mongo.dao.sharding.HashShardingMongoDao
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * Drive 模块 HashShardingMongoDao 基类，使用 drive 专用 MongoTemplate。
 */
abstract class DriveHashShardingMongoDao<E> : HashShardingMongoDao<E>() {

    @Autowired
    @Qualifier("driveMongoTemplate")
    private lateinit var driveMongoTemplate: MongoTemplate

    override fun determineMongoTemplate(): MongoTemplate {
        return driveMongoTemplate
    }
}
