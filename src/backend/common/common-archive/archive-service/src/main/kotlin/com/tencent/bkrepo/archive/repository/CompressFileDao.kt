package com.tencent.bkrepo.archive.repository

import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class CompressFileDao : SimpleMongoDao<TCompressFile>() {
    fun findByStorageKeyAndSha256(storageKey: String?, sha256: String): TCompressFile? {
        val criteria = Criteria.where(TCompressFile::storageCredentialsKey.name).isEqualTo(storageKey)
            .and(TCompressFile::sha256.name).isEqualTo(sha256)
        return findOne(Query(criteria))
    }
}
