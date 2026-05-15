package com.tencent.bkrepo.repository.dao

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.repository.model.TClientVersionConfig
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

@Repository
class ClientVersionConfigDao : SimpleMongoDao<TClientVersionConfig>() {

    fun findByKey(
        productId: String,
        platform: String,
        arch: String,
        targetUserId: String?,
    ): TClientVersionConfig? {
        return findOne(Query.query(keyCriteria(productId, platform, arch, targetUserId)))
    }

    fun listByProductId(productId: String?): List<TClientVersionConfig> {
        if (productId.isNullOrBlank()) {
            return findAll()
        }
        val crit = Criteria.where(TClientVersionConfig::productId.name).`is`(productId)
        return find(Query.query(crit))
    }

    fun existsByProductIdAndUser(productId: String, targetUserId: String): Boolean {
        val crit = Criteria.where(TClientVersionConfig::productId.name).`is`(productId)
            .and(TClientVersionConfig::targetUserId.name).`is`(targetUserId)
        return exists(Query.query(crit))
    }

    fun existsByEnabledProductIdAndUser(
        productId: String,
        targetUserId: String,
    ): Boolean {
        val crit = Criteria
            .where(TClientVersionConfig::productId.name).`is`(productId)
            .and(TClientVersionConfig::targetUserId.name).`is`(targetUserId)
            .and(TClientVersionConfig::enabled.name).`is`(true)
        return exists(Query.query(crit))
    }

    fun existsEnabledUserConfigByScope(
        productId: String,
        platform: String,
        arch: String,
    ): Boolean {
        val base = Criteria.where(TClientVersionConfig::productId.name).`is`(productId)
            .and(TClientVersionConfig::platform.name).`is`(platform)
            .and(TClientVersionConfig::arch.name).`is`(arch)
            .and(TClientVersionConfig::enabled.name).`is`(true)
            .and(TClientVersionConfig::targetUserId.name).ne(null)
        return exists(Query.query(base))
    }

    fun forEachInBatches(
        batchSize: Int,
        action: (TClientVersionConfig) -> Unit,
    ) {
        var skip = 0L
        while (true) {
            val batch = find(Query().skip(skip).limit(batchSize))
            if (batch.isEmpty()) break
            batch.forEach(action)
            if (batch.size < batchSize) break
            skip += batchSize
        }
    }

    fun listByProductIdAndNullUser(productId: String): List<TClientVersionConfig> {
        val crit = Criteria.where(TClientVersionConfig::productId.name).`is`(productId)
            .andOperator(nullTargetUserCriteria())
        return find(Query.query(crit))
    }

    fun pageByProductId(productId: String?, pageNumber: Int, pageSize: Int): Page<TClientVersionConfig> {
        val query = buildProductIdQuery(productId)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val totalRecords = count(query)
        val records = find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    private fun nullTargetUserCriteria(): Criteria {
        return Criteria.where(TClientVersionConfig::targetUserId.name).`is`(null)
    }

    private fun buildProductIdQuery(productId: String?): Query {
        val query = if (productId.isNullOrBlank()) {
            Query()
        } else {
            Query.query(Criteria.where(TClientVersionConfig::productId.name).`is`(productId))
        }
        query.with(
            Sort.by(
                Sort.Order.desc(TClientVersionConfig::lastModifiedDate.name),
                Sort.Order.desc("_id"),
            ),
        )
        return query
    }

    private fun keyCriteria(
        productId: String,
        platform: String,
        arch: String,
        targetUserId: String?,
    ): Criteria {
        val base = Criteria.where(TClientVersionConfig::productId.name).`is`(productId)
            .and(TClientVersionConfig::platform.name).`is`(platform)
            .and(TClientVersionConfig::arch.name).`is`(arch)
        val scopeKey = if (targetUserId == null) {
            nullTargetUserCriteria()
        } else {
            Criteria.where(TClientVersionConfig::targetUserId.name).`is`(targetUserId)
        }
        return Criteria().andOperator(base, scopeKey)
    }
}
