package com.tencent.bkrepo.generic.repository

import com.tencent.bkrepo.generic.model.TDownloadTokenRecord
import org.springframework.data.mongodb.repository.MongoRepository

interface DownloadTokenRepository : MongoRepository<TDownloadTokenRecord, String> {
    fun findOneByToken(name: String): TDownloadTokenRecord?
}
