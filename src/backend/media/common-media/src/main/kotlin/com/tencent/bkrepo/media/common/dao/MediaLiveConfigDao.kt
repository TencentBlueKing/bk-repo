package com.tencent.bkrepo.media.common.dao

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.media.common.model.TMediaLiveConfig
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import org.springframework.data.mongodb.core.query.Criteria.where as strWhere

@Repository
class MediaLiveConfigDao : SimpleMongoDao<TMediaLiveConfig>() {

    /**
     * 判断是否启用新的直播模式
     * projectId、userId、workspaceId 任一命中已启用的配置即返回 true
     */
    fun isLiveModeEnabled(projectId: String, userId: String, workspaceId: String): Boolean {
        val query = Query(
            Criteria().andOperator(
                where(TMediaLiveConfig::enabled).isEqualTo(true),
                Criteria().orOperator(
                    where(TMediaLiveConfig::projectId).isEqualTo(projectId),
                    where(TMediaLiveConfig::userId).isEqualTo(userId),
                    where(TMediaLiveConfig::workspaceId).isEqualTo(workspaceId),
                )
            )
        )
        return exists(query)
    }

    /**
     * 根据 projectId、userId、workspaceId 三选一查询配置
     */
    fun findConfig(projectId: String?, userId: String?, workspaceId: String?): TMediaLiveConfig? {
        val criteria = mutableListOf<Criteria>()
        projectId?.let { criteria.add(where(TMediaLiveConfig::projectId).isEqualTo(it)) }
        userId?.let { criteria.add(where(TMediaLiveConfig::userId).isEqualTo(it)) }
        workspaceId?.let { criteria.add(where(TMediaLiveConfig::workspaceId).isEqualTo(it)) }
        if (criteria.isEmpty()) return null
        val query = Query(Criteria().orOperator(*criteria.toTypedArray()))
        return findOne(query)
    }

    /**
     * 查询所有配置
     */
    fun findAllConfigs(): List<TMediaLiveConfig> {
        return find(Query())
    }

    /**
     * 更新配置的启用状态
     */
    fun updateConfig(
        id: String,
        projectId: String?,
        userId: String?,
        workspaceId: String?,
        enabled: Boolean,
        updatedBy: String,
    ): UpdateResult {
        val query = Query(strWhere("_id").isEqualTo(ObjectId(id)))
        val update = Update()
            .set(TMediaLiveConfig::projectId.name, projectId)
            .set(TMediaLiveConfig::userId.name, userId)
            .set(TMediaLiveConfig::workspaceId.name, workspaceId)
            .set(TMediaLiveConfig::enabled.name, enabled)
            .set(TMediaLiveConfig::updatedBy.name, updatedBy)
            .set(TMediaLiveConfig::updateTime.name, LocalDateTime.now())
        return updateFirst(query, update)
    }

    /**
     * 删除配置
     */
    fun deleteConfig(id: String): DeleteResult {
        val query = Query(strWhere("_id").isEqualTo(ObjectId(id)))
        return remove(query)
    }
}
