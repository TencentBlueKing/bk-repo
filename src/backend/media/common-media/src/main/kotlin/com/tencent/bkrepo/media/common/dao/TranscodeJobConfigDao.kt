package com.tencent.bkrepo.media.common.dao

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.media.common.model.TMediaTranscodeJobConfig
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Repository
import org.springframework.data.mongodb.core.query.Criteria.where as strWhere

@Repository
class TranscodeJobConfigDao : SimpleMongoDao<TMediaTranscodeJobConfig>() {

    /**
     * 根据项目ID查询配置
     */
    fun findByProjectId(projectId: String?): TMediaTranscodeJobConfig? {
        val query = Query(where(TMediaTranscodeJobConfig::projectId).isEqualTo(projectId))
        return findOne(query)
    }

    /**
     * 查询所有配置
     */
    fun findAllConfigs(): List<TMediaTranscodeJobConfig> {
        return find(Query())
    }

    /**
     * 更新配置
     */
    fun updateConfig(
        id: String,
        projectId: String?,
        maxJobCount: Int?,
        image: String?,
        resource: String?,
        cosConfigMapName: String?,
    ): UpdateResult {
        val query = Query(strWhere("_id").isEqualTo(ObjectId(id)))
        val update = Update()
        update.set(TMediaTranscodeJobConfig::projectId.name, projectId)
        maxJobCount?.let { update.set(TMediaTranscodeJobConfig::maxJobCount.name, it) }
        image?.let { update.set(TMediaTranscodeJobConfig::image.name, it) }
        update.set(TMediaTranscodeJobConfig::resource.name, resource)
        update.set(TMediaTranscodeJobConfig::cosConfigMapName.name, cosConfigMapName)
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
