package com.tencent.bkrepo.helm.dao

import com.tencent.bkrepo.helm.model.TMongoLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Repository
class MongoLockDao {

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    /**
     * 返回指定key的数据
     */
    fun getByKey(lockKey: String, lockValue: String): TMongoLock? {
        val criteria = Criteria.where(TMongoLock::key.name).`is`(lockKey)
            .and(TMongoLock::requestId.name).`is`(lockValue)
        return mongoTemplate.findOne(Query(criteria), TMongoLock::class.java)
    }

    /**
     * 指定key自增increment(原子加),并设置过期时间
     */
    fun incrByWithExpire(key: String, lockValue: String, expire: Long): Boolean {
        val criteria = Criteria.where(TMongoLock::key.name).`is`(key).and(TMongoLock::requestId.name).`is`(lockValue)
        val query = Query(criteria)

        val update = Update().inc(TMongoLock::value.name, 1)
            .set(TMongoLock::expire.name, LocalDateTime.now())
        // 可选项
        val options = FindAndModifyOptions.options()
        // 没有则新增
        options.upsert(true)
        // 返回更新后的值
        options.returnNew(true)
        return try {
            mongoTemplate.indexOps(TMongoLock::class.java).ensureIndex(
                Index().named(INDEX_KEY_EXPIRE).on(TMongoLock::expire.name,
                    Sort.Direction.ASC).expire(expire, TimeUnit.SECONDS))
            val value = mongoTemplate.findAndModify(query, update, options, TMongoLock::class.java)?.value!!
            value == 1
        } catch (ex: RuntimeException) {
            logger.error("get lock failed,${ex.message}")
            false
        }
    }

    fun releaseLock(lockKey: String, lockValue: String): Boolean {
        val criteria = Criteria.where(TMongoLock::key.name).`is`(lockKey)
            .and(TMongoLock::requestId.name).`is`(lockValue)
        val query = Query(criteria)
        return mongoTemplate.remove(query, TMongoLock::class.java).wasAcknowledged()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MongoLockDao::class.java)
        const val INDEX_KEY_EXPIRE = "mongo_key_expire"
    }
}
