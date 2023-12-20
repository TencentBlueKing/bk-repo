package com.tencent.bkrepo.archive.repository

import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.model.TCompressFile
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CompressFileRepository : MongoRepository<TCompressFile, String> {
    fun findBySha256AndStorageCredentialsKey(sha256: String, key: String?): TCompressFile?
    fun countByStatus(status: CompressStatus): Int
}
