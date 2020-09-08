package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest

/**
 * 元数据服务
 */
interface MetadataService {
    fun query(projectId: String, repoName: String, fullPath: String): Map<String, String>
    fun save(request: MetadataSaveRequest)
    fun delete(request: MetadataDeleteRequest)
}
