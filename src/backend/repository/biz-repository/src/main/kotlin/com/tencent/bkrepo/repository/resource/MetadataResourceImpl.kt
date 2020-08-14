package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.MetadataResource
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.service.MetadataService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 * 元数据接口实现类
 */
@RestController
class MetadataResourceImpl @Autowired constructor(
    private val metadataService: MetadataService
) : MetadataResource {
    override fun query(projectId: String, repoName: String, fullPath: String): Response<Map<String, String>> {
        return ResponseBuilder.success(metadataService.query(projectId, repoName, fullPath))
    }

    override fun save(metadataSaveRequest: MetadataSaveRequest): Response<Void> {
        metadataService.save(metadataSaveRequest)
        return ResponseBuilder.success()
    }

    override fun delete(metadataDeleteRequest: MetadataDeleteRequest): Response<Void> {
        metadataService.delete(metadataDeleteRequest)
        return ResponseBuilder.success()
    }
}
