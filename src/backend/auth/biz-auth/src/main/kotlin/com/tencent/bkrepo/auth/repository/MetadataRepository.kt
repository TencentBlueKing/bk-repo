package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TMetadata
import com.tencent.bkrepo.auth.pojo.Metadata
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

/**
 * 元数据 mongo repository
 *
 * @author: carrypan
 * @date: 2019-09-26
 */
@Repository
interface MetadataRepository : MongoRepository<TMetadata, String> {
    fun findByNodeId(nodeId: String): List<Metadata>
    fun deleteByNodeId(nodeId: String)
    fun deleteByIdIn(it: List<String>): Unit?
}
