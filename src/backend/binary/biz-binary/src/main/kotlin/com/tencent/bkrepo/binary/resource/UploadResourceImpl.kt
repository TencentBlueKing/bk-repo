package com.tencent.bkrepo.binary.resource

import com.tencent.bkrepo.binary.api.UploadResource
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.web.bind.annotation.RestController

/**
 * 二进制上传服务接口实现类
 *
 * @author: carrypan
 * @date: 2019-09-27
 */
@RestController
class UploadResourceImpl: UploadResource {
    override fun detail(name: String): Response<String> {
        return Response.success("Hello, $name")
    }

}
