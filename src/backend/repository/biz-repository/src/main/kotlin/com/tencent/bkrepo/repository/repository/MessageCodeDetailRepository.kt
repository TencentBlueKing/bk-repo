package com.tencent.bkrepo.repository.repository

import com.tencent.bkrepo.repository.model.TMessageCodeDetail
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

/**
 * 消息码repository
 *
 * @author: carrypan
 * @date: 2019-10-09
 */
@Repository
interface MessageCodeDetailRepository : MongoRepository<TMessageCodeDetail, String> {
    fun findByMessageCode(messageCode: String): TMessageCodeDetail?
}
