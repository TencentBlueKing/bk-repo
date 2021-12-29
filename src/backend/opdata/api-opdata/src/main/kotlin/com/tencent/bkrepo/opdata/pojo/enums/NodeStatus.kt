package com.tencent.bkrepo.opdata.pojo.enums

/**
 * 节点状态
 */
enum class NodeStatus {
    /**
     * 正常运行
     */
    RUNNING,

    /**
     * 已从注册中心下线
     */
    DEREGISTER,

    /**
     * 离线，无法连通
     */
    OFFLINE;
}
