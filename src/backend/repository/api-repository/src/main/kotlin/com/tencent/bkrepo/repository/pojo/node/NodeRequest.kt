package com.tencent.bkrepo.repository.pojo.node

/**
 * 节点请求抽象类
 *
 * @author: carrypan
 * @date: 2019/11/8
 */
interface NodeRequest {
    val projectId: String
    val repoName: String
    val fullPath: String
}
