package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.model.TRepository

/**
 * 文件引用服务接口
 */
interface FileReferenceService {
    /**
     * 增加文件sha256的引用数量
     *
     * sha256为[node]的属性，[repository]不为空则取credentialsKey属性，否则从[node]中取出repoName后从数据库查询
     * 增加引用成功则返回`true`; 如果[node]为目录，返回`false`
     */
    fun increment(node: TNode, repository: TRepository? = null): Boolean

    /**
     * 减少sha256的引用数量
     *
     * sha256为[node]的属性，[repository]不为空则取credentialsKey属性，否则从[node]中取出repoName后从数据库查询
     * 减少引用成功则返回`true`; 如果[node]为目录，返回`false`; 如果当前sha256的引用已经为0，返回`false`
     */
    fun decrement(node: TNode, repository: TRepository? = null): Boolean

    /**
     * 增加文件[sha256]在存储实例[credentialsKey]上的引用数量
     *
     * [credentialsKey]为`null`则使用默认的存储实例
     * 增加引用成功则返回`true`
     */
    fun increment(sha256: String, credentialsKey: String?): Boolean

    /**
     * 减少文件[sha256]在存储实例[credentialsKey]上的文件数量
     *
     * [credentialsKey]为`null`则使用默认的存储实例
     * 减少引用成功则返回`true`，如果当前[sha256]的引用已经为0，返回`false`
     */
    fun decrement(sha256: String, credentialsKey: String?): Boolean


    /**
     * 统计文件[sha256]在存储实例[credentialsKey]上的文件引用数量
     *
     * [credentialsKey]为`null`则使用默认的存储实例
     */
    fun count(sha256: String, credentialsKey: String?): Long
}

