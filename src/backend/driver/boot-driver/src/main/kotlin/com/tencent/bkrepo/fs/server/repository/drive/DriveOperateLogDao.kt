package com.tencent.bkrepo.fs.server.repository.drive

import com.tencent.bkrepo.common.metadata.model.TOperateLog
import com.tencent.bkrepo.common.mongo.reactive.dao.MonthRangeShardingMongoReactiveDao
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Component

/**
 * Drive 操作审计日志 DAO
 *
 * 复用 drive 专用的 ReactiveMongoTemplate，审计日志随 Drive 业务数据写入同一独立数据库。
 */
@Component
class DriveOperateLogDao : MonthRangeShardingMongoReactiveDao<TOperateLog>() {

    @Autowired
    @Qualifier("driveReactiveMongoTemplate")
    lateinit var driveReactiveMongoTemplate: ReactiveMongoTemplate

    override fun determineReactiveMongoOperations(): ReactiveMongoTemplate {
        return driveReactiveMongoTemplate
    }
}
