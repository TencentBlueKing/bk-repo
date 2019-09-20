package com.tencent.bkrepo.metadata.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.metadata.api.MetadataResource
import com.tencent.bkrepo.metadata.pojo.Metadata
import org.springframework.web.bind.annotation.RestController

/**
 * 元数据服务接口实现类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@RestController
class MetadataResourceImpl : MetadataResource {
    override fun detail(id: String): Response<Metadata> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun list(nodeId: String): Response<List<Metadata>> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun create(repository: Metadata): Response<Metadata> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun create(repository: List<Metadata>): Response<List<Metadata>> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun update(id: String, repository: Metadata): Response<Boolean> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun delete(id: String): Response<Boolean> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }
}
