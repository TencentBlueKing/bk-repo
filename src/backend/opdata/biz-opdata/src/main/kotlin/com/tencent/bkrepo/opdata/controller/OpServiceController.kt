/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.annotation.LogOperate
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder.success
import com.tencent.bkrepo.opdata.pojo.bandwidth.BandwidthInfo
import com.tencent.bkrepo.opdata.pojo.registry.InstanceInfo
import com.tencent.bkrepo.opdata.pojo.registry.ServiceInfo
import com.tencent.bkrepo.opdata.registry.consul.pojo.ConsulKeyValue
import com.tencent.bkrepo.opdata.service.OpServiceService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 服务管理
 */
@RestController
@RequestMapping("/api/services")
@Principal(PrincipalType.ADMIN)
class OpServiceController @Autowired constructor(
    private val opServiceService: OpServiceService,
) {

    /**
     * 列出当前注册中心中的所有服务
     */
    @GetMapping
    @LogOperate(type = "SERVICE_LIST")
    fun services(): Response<List<ServiceInfo>> {
        return success(opServiceService.listServices())
    }

    /**
     * 获取服务实例信息
     */
    @GetMapping("/{serviceName}/instances")
    @LogOperate(type = "SERVICE_INSTANCE_LIST")
    fun instances(@PathVariable("serviceName") serviceName: String): Response<List<InstanceInfo>> {
        return success(opServiceService.instances(serviceName))
    }

    @GetMapping("/{serviceName}/instances/{instanceId}")
    @LogOperate(type = "SERVICE_INSTANCE")
    fun instance(
        @PathVariable serviceName: String,
        @PathVariable instanceId: String,
    ): Response<InstanceInfo> {
        return success(opServiceService.instance(serviceName, instanceId))
    }

    /**
     * 下线服务节点
     */
    @PostMapping("/{serviceName}/instances/{instanceId}/down")
    @LogOperate(type = "SERVICE_INSTANCE_DOWN")
    fun downInstance(
        @PathVariable serviceName: String,
        @PathVariable instanceId: String,
    ): Response<InstanceInfo> {
        return success(opServiceService.changeInstanceStatus(serviceName, instanceId, true))
    }

    /**
     * 上线服务节点
     */
    @PostMapping("/{serviceName}/instances/{instanceId}/up")
    @LogOperate(type = "SERVICE_INSTANCE_UP")
    fun upInstance(
        @PathVariable serviceName: String,
        @PathVariable instanceId: String,
    ): Response<InstanceInfo> {
        return success(opServiceService.changeInstanceStatus(serviceName, instanceId, false))
    }


    /**
     * 获取服务当前带宽大小
     */
    @GetMapping("/{serviceName}/bandwidth")
    @LogOperate(type = "SERVICE_INSTANCE_BANDWIDTH_LIST")
    fun serviceBandwidth(@PathVariable("serviceName") serviceName: String): Response<List<BandwidthInfo>> {
        return success(opServiceService.serviceBandwidth(serviceName))
    }

    /**
     * 根据服务和ip删除对应存储的带宽数据
     */
    @DeleteMapping("/{serviceName}/bandwidth/{hostIp}/delete")
    @LogOperate(type = "SERVICE_INSTANCE_BANDWIDTH_DELETE")
    fun deleteBandwidthDataByServiceAndIp(
        @PathVariable serviceName: String,
        @PathVariable hostIp: String,
    ): Response<Void> {
        opServiceService.deleteDataByServiceAndIp(serviceName, hostIp)
        return success()
    }

    /**
     * 根据发服务获取带宽最小的ip列表
     */
    @GetMapping("/{serviceName}/bandwidth/ips")
    @LogOperate(type = "SERVICE_INSTANCE_BANDWIDTH_IP_LIST")
    fun serviceBandwidthIps(
        @PathVariable("serviceName") serviceName: String,
        @RequestParam(required = false, defaultValue = "300")
        activeSeconds: Long = 300,
        @RequestParam(required = false, defaultValue = "false")
        returnAll: Boolean = false,
    ): Response<List<String>> {
        return success(opServiceService.serviceBandwidthIps(serviceName, activeSeconds, returnAll))
    }

    @GetMapping("/isConsul")
    fun checkConsulPattern(): Response<Boolean> {
        return success(opServiceService.checkConsulAlive())
    }

    @GetMapping("/configs")
    fun serviceConfig(): Response<List<ConsulKeyValue>> {
        return success(opServiceService.getConfigs())
    }
}
