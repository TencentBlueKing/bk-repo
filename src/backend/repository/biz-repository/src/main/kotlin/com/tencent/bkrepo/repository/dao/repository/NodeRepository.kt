package com.tencent.bkrepo.repository.dao.repository

import com.tencent.bkrepo.repository.model.TNode
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

/**
 * 节点mongo repository
 */
@Repository
interface NodeRepository : MongoRepository<TNode, String>
