package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest

/**
 * 元数据服务接口
 */
interface MetadataService {

    /**
     * 查询节点的元数据
     *
     * [projectId]为节点所属项目，[repoName]为节点所属仓库，[fullPath]为节点完整路径
     * 返回[Map]数据结构，`key`为元数据名称，`value`为元数据值
     */
    fun query(projectId: String, repoName: String, fullPath: String): Map<String, Any>

    /**
     * 根据请求[request]保存或者更新元数据
     *
     * 如果元数据`key`已经存在则更新，否则创建新的
     */
    fun save(request: MetadataSaveRequest)

    /**
     * 根据请求[request]删除元数据
     */
    fun delete(request: MetadataDeleteRequest)
}
