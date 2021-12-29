package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.opdata.pojo.service.NodeInfo
import com.tencent.bkrepo.opdata.pojo.service.ServiceInfo
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 服务管理
 */
@RestController
@RequestMapping("/api/op/services")
class ServiceController {

    /**
     * 列出当前注册中心中的所有服务
     */
    @GetMapping
    fun listServices(): Response<List<ServiceInfo>> {
        TODO()
    }

    /**
     * 获取服务实例信息
     */
    @GetMapping("/{serviceName}")
    fun listNodes(@PathVariable("serviceName") serviceName: String): Response<List<NodeInfo>> {
        TODO()
    }

    /**
     * 获取节点当前运行状态详情
     */
    @GetMapping("/{serviceName}/nodes/{nodeId}")
    fun nodeStatus(@PathVariable serviceName: String, @PathVariable nodeId: String): Response<NodeInfo> {
        TODO()
    }

    /**
     * 上线服务节点
     */
    @PostMapping("/{serviceName}/nodes/{nodeId}/up")
    fun upNode(@PathVariable serviceName: String, @PathVariable nodeId: String): Response<NodeInfo> {
        TODO()
    }


    /**
     * 下线服务节点
     */
    @PostMapping("/{serviceName}/nodes/{nodeId}/down")
    fun downNode(@PathVariable serviceName: String, @PathVariable nodeId: String): Response<NodeInfo> {
        TODO()
    }
}
