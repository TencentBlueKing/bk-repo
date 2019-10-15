package com.tencent.bkrepo.repository.repository

import com.tencent.bkrepo.repository.model.TNode
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

/**
 * 节点mongo repository
 *
 * @author: carrypan
 * @date: 2019-09-20
 */
@Repository
interface NodeRepository : MongoRepository<TNode, String>
