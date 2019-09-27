package com.tencent.bkrepo.repository.repository

import com.tencent.bkrepo.repository.model.TFileBlock
import com.tencent.bkrepo.repository.pojo.FileBlock
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

/**
 * 仓库mongo repository
 *
 * @author: carrypan
 * @date: 2019-09-20
 */
@Repository
interface FileBlockRepository : MongoRepository<TFileBlock, String> {
    fun findByNodeId(nodeId: String): List<FileBlock>
}
