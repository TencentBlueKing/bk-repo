package com.tencent.bkrepo.repository.pojo.node

/**
 * 节点操作抽象类
 *
 * @author: carrypan
 * @date: 2019/11/8
 */
abstract class NodeOperateRequest {
    abstract val srcProjectId: String
    abstract val srcRepoName: String
    abstract val srcFullPath: String
    abstract val destProjectId: String?
    abstract val destRepoName: String?
    abstract val destPath: String
    abstract val overwrite: Boolean

    abstract val operator: String

    abstract fun getOperateName(): String
}