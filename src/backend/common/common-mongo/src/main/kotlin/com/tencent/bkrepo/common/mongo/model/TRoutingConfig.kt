package com.tencent.bkrepo.common.mongo.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("mongo_routing_config")
data class TRoutingConfig(
    @Id
    var id: String = SINGLETON_ID,
    /** 同时双写的项目数上限（跨规则全局限制） */
    var maxConcurrentDualWrite: Int = 1,
    /** 迁移期禁止 DDL */
    var freezeDdl: Boolean = false,
    /** DB 配置版本号，每次变更时递增，供运维对账 */
    var configVersion: Long = 0,
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        const val SINGLETON_ID = "singleton"
    }
}
