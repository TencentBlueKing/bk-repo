package com.tencent.bkrepo.common.mongo.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.mongo.model.TRoutingConfig
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class RoutingConfigDao : SimpleMongoDao<TRoutingConfig>() {

    fun get(): TRoutingConfig = findById(TRoutingConfig.SINGLETON_ID)
        ?: TRoutingConfig()

    override fun save(config: TRoutingConfig): TRoutingConfig {
        val doc = config.copy(updatedAt = LocalDateTime.now())
        val query = Query(TRoutingConfig::id.isEqualTo(TRoutingConfig.SINGLETON_ID))
        val update = Update()
            .set(TRoutingConfig::maxConcurrentDualWrite.name, doc.maxConcurrentDualWrite)
            .set(TRoutingConfig::freezeDdl.name, doc.freezeDdl)
            .set(TRoutingConfig::configVersion.name, doc.configVersion)
            .set(TRoutingConfig::updatedAt.name, doc.updatedAt)
        upsert(query, update)
        return doc
    }
}
