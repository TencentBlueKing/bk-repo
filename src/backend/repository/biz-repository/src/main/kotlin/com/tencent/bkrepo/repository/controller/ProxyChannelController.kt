package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.ProxyChannelClient
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelInfo
import com.tencent.bkrepo.repository.service.ProxyChannelService
import org.springframework.web.bind.annotation.RestController

/**
 * 代理源服务接口实现类
 */
@RestController
class ProxyChannelController(
    private val proxyChannelService: ProxyChannelService
) : ProxyChannelClient {

    override fun getById(id: String): Response<ProxyChannelInfo?> {
        return ResponseBuilder.success(proxyChannelService.findById(id))
    }
}
