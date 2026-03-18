package com.tencent.bkrepo.fs.server.repository.drive

import com.tencent.bkrepo.common.mongo.reactive.dao.HashShardingMongoReactiveDao
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

/**
 * Drive 模块 HashShardingMongoReactiveDao 基类
 *
 * 使用 drive 专用的 ReactiveMongoTemplate，支持将数据写到单独数据库中。
 */
abstract class DriveHashShardingMongoReactiveDao<E> : HashShardingMongoReactiveDao<E>() {

    @Autowired
    @Qualifier("driveReactiveMongoTemplate")
    lateinit var driveReactiveMongoTemplate: ReactiveMongoTemplate

    override fun determineReactiveMongoOperations(): ReactiveMongoTemplate {
        return driveReactiveMongoTemplate
    }
}
