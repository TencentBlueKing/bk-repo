package com.tencent.bkrepo.repository.pojo.node

/**
 * 节点请求抽象类
 */
interface NodeRequest {
    val projectId: String
    val repoName: String
    val fullPath: String
}
