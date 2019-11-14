package com.tencent.bkrepo.repository.pojo.node

/**
 * 节点请求抽象类
 *
 * @author: carrypan
 * @date: 2019/11/8
 */
abstract class BaseNodeRequest {
    abstract val projectId: String
    abstract val repoName: String
    abstract val fullPath: String
}
