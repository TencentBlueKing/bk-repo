package com.tencent.bkrepo.archive.repository

import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class ArchiveFileDao : SimpleMongoDao<TArchiveFile>() {
    fun findByStorageKeyAndSha256(storageKey: String?, sha256: String): TArchiveFile? {
        val criteria = Criteria.where(TArchiveFile::storageCredentialsKey.name).isEqualTo(storageKey)
            .and(TArchiveFile::sha256.name).isEqualTo(sha256)
        return findOne(Query(criteria))
    }

    fun countByArchiveKeyAndSha256(archiveKey: String?, sha256: String): Long {
        val criteria = Criteria
            .where(TArchiveFile::archiveCredentialsKey.name).isEqualTo(archiveKey)
            .and(TArchiveFile::sha256.name).isEqualTo(sha256)
        return count(Query(criteria))
    }
}
