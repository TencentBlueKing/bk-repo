package com.tencent.bkrepo.repository.pojo.node

/**
 * 节点请求抽象类
 *
 * @author: carrypan
 * @date: 2019/11/8
 */
interface CrossRepoNodeRequest {
    val srcProjectId: String
    val srcRepoName: String
    val srcFullPath: String
    val destProjectId: String?
    val destRepoName: String?
    val destFullPath: String
    val overwrite: Boolean

    fun getOperateName(): String
}
