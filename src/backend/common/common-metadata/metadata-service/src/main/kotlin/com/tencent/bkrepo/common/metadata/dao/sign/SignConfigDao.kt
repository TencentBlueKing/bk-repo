package com.tencent.bkrepo.common.metadata.dao.sign

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.model.TSignConfig
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

/**
 * 签名配置数据访问层
 */
@Repository
@Conditional(SyncCondition::class)
class SignConfigDao : SimpleMongoDao<TSignConfig>() {

    /**
     * 根据项目ID[projectId]查找签名配置
     */
    fun findByProjectId(projectId: String): TSignConfig? {
        return this.findOne(Query(TSignConfig::projectId.isEqualTo(projectId)))
    }

    /**
     * 根据项目ID[projectId]判断签名配置是否存在
     */
    fun existsByProjectId(projectId: String): Boolean {
        return this.exists(Query(TSignConfig::projectId.isEqualTo(projectId)))
    }

    /**
     * 根据项目ID[projectId]删除签名配置
     */
    fun deleteByProjectId(projectId: String): Boolean {
        return this.remove(Query(TSignConfig::projectId.isEqualTo(projectId))).deletedCount > 0
    }
}
