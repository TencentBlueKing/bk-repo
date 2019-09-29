package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.auth.api.MetadataResource
import com.tencent.bkrepo.auth.pojo.Metadata
import com.tencent.bkrepo.auth.pojo.MetadataDeleteRequest
import com.tencent.bkrepo.auth.pojo.MetadataUpsertRequest
import com.tencent.bkrepo.auth.service.MetadataService
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
