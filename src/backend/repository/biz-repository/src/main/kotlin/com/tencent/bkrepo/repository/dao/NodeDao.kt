package com.tencent.bkrepo.repository.dao

import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingMongoDao
import com.tencent.bkrepo.repository.model.TNode
import org.springframework.stereotype.Repository

/**
 * 节点 Dao
 *
 * @author: carrypan
 * @date: 2019/11/7
 */
@Repository
class NodeDao : ShardingMongoDao<TNode>()
