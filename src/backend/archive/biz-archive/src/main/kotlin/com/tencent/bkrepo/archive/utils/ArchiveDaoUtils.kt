package com.tencent.bkrepo.archive.utils

import com.tencent.bkrepo.archive.model.AbstractEntity
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.LocalDateTime

/**
 * 归档DAO工具
 * */
object ArchiveDaoUtils {
    /**
     * 乐观锁
     * 通过cap实现乐观锁
     * @param entity 操作实体
     * @param key 需要原子更新的key
     * @param expect 期望值
     * @param update 更改值
     * @return true更改成功，false更改失败
     * */
    fun <E : AbstractEntity> AbstractMongoDao<E>.optimisticLock(
        entity: E,
        key: String,
        expect: Any,
        update: Any,
    ): Boolean {
        val criteria = Criteria.where(ID).isEqualTo(ObjectId(entity.id)).and(key).isEqualTo(expect)
        val newUpdate = Update().set(key, update)
            .set(entity::lastModifiedDate.name, LocalDateTime.now())
        return this.updateFirst(Query.query(criteria), newUpdate).modifiedCount == 1L
    }
}
