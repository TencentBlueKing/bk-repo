package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.replication.model.TClusterNode
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeType
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo

/**
 * 节点查询条件构造工具
 */
object ClusterQueryHelper {
    fun clusterListQuery(name: String? = null, type: ClusterNodeType? = null): Query {
        val criteria = Criteria()
        name?.let { criteria.and(TClusterNode::name.name).regex("^$name") }
        type?.let { criteria.and(TClusterNode::type.name).isEqualTo(type) }
        return Query(criteria).with(Sort.by(Sort.Order(Sort.Direction.DESC, TClusterNode::createdDate.name)))
    }
}
