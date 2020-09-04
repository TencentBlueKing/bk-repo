package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelInfo
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 代理源服务接口
 */
@Primary
@FeignClient(SERVICE_NAME, contextId = "ProxyChannelClient")
@RequestMapping("/service/proxy-channel")
interface ProxyChannelClient {

    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): Response<ProxyChannelInfo?>
}
