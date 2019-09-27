package com.tencent.bkrepo.metadata.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.metadata.api.MetadataResource
import com.tencent.bkrepo.metadata.pojo.Metadata
import com.tencent.bkrepo.metadata.pojo.MetadataDeleteRequest
import com.tencent.bkrepo.metadata.pojo.MetadataUpsertRequest
import com.tencent.bkrepo.metadata.service.MetadataService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 * 元数据服务接口实现类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@RestController
class MetadataResourceImpl @Autowired constructor(
        private val metadataService: MetadataService
) : MetadataResource {
    override fun detail(id: String): Response<Metadata> {
        return Response.success(metadataService.getDetailById(id))
    }

    override fun list(nodeId: String): Response<List<Metadata>> {
        return Response.success(metadataService.list(nodeId))
    }

    override fun upsert(metadataUpsertRequest: MetadataUpsertRequest): Response<Void> {
        metadataService.upsert(metadataUpsertRequest)
        return Response.success()
    }

    override fun delete(metadataDeleteRequest: MetadataDeleteRequest): Response<Void> {
        metadataService.delete(metadataDeleteRequest)
        return Response.success()
    }
}
