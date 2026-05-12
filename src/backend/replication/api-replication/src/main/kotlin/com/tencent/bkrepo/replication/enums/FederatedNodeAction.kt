package com.tencent.bkrepo.replication.enums

/**
 * 联邦仓库节点同步冲突处理动作
 */
enum class FederatedNodeAction {
    /** 直接创建节点 */
    CREATE,
    /** 跳过，保留目标节点 */
    SKIP,
    /** 覆盖目标节点 */
    OVERWRITE,
    /** sha256 相同，只合并元数据 */
    MERGE_METADATA
}