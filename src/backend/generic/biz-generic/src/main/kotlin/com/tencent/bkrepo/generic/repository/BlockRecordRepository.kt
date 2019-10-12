package com.tencent.bkrepo.generic.repository

import com.tencent.bkrepo.generic.model.TBlockRecord
import org.springframework.data.mongodb.repository.MongoRepository

/**
 * 分块记录 repository
 *
 * @author: carrypan
 * @date: 2019-10-10
 */
interface BlockRecordRepository : MongoRepository<TBlockRecord, String> {
    fun findByUploadIdAndSequence(uploadId: String, sequence: Int): TBlockRecord?
    fun findByUploadId(uploadId: String): List<TBlockRecord>
    fun deleteByUploadId(uploadId: String)
}
