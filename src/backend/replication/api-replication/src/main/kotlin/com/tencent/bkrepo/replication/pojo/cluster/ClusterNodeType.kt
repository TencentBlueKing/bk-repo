package com.tencent.bkrepo.replication.pojo.cluster

/**
 * 集群类型
 */
enum class ClusterNodeType {
    // 中心节点
    CENTER,
    // 边缘节点
    EDGE,
    // 独立集群
    STANDALONE
}
