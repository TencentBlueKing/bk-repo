package com.tencent.bkrepo.helm.dao

import com.tencent.bkrepo.helm.model.TMongoLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

@Repository
class MongoLockDao {
    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    /**
     * 返回指定key的数据
     */
    fun getByKey(key: String): List<TMongoLock> {
        val criteria = Criteria.where(TMongoLock::key.name).`is`(key)
        return mongoTemplate.find(Query(criteria), TMongoLock::class.java)
    }

    /**
     * 指定key自增increment(原子加),并设置过期时间
     */
    fun incrByWithExpire(key: String, increment: Int, expire: Long): Map<String, Any> {
        val criteria = Criteria.where(TMongoLock::key.name).`is`(key)
        val query = Query(criteria)

        val update = Update().inc(TMongoLock::value.name, increment).set(TMongoLock::expire.name, expire)
        // 可选项
        val options = FindAndModifyOptions.options()
        // 没有则新增
        options.upsert(true)
        // 返回更新后的值
        options.returnNew(true)
        val resultMap = mutableMapOf<String, Any>()
        resultMap[TMongoLock::value.name] =
            mongoTemplate.findAndModify(query, update, options, TMongoLock::class.java)?.value!!
        resultMap[TMongoLock::expire.name] =
            mongoTemplate.findAndModify(query, update, options, TMongoLock::class.java)?.expire!!
        return resultMap
    }

    /**
     * 根据value删除过期的内容
     *
     * @param key
     * @param expireTime
     */
    fun removeExpire(key: String, expireTime: Long) {
        val query = Query()
        query.addCriteria(Criteria.where(TMongoLock::key.name).`is`(key))
        query.addCriteria(Criteria.where(TMongoLock::expire.name).lt(expireTime))
        mongoTemplate.remove(query, TMongoLock::class.java)
    }

    fun remove(condition: Map<String, Any>) {
        val query = Query()
        val set = condition.entries
        var flag = 0
        for ((key, value) in set) {
            query.addCriteria(Criteria.where(key).`is`(value))
            flag += 1
        }
        if (flag > 0) {
            mongoTemplate.remove(query, TMongoLock::class.java)
        }
    }
}
