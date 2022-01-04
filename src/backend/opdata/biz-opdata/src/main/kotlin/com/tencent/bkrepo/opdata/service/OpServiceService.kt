/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.common.api.constant.HttpStatus.CONFLICT
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.opdata.message.OpDataMessageCode.ServiceInstanceDeregisterConflict
import com.tencent.bkrepo.opdata.model.TOpDeregisterServiceInstance
import com.tencent.bkrepo.opdata.pojo.registry.InstanceInfo
import com.tencent.bkrepo.opdata.pojo.registry.ServiceInfo
import com.tencent.bkrepo.opdata.registry.RegistryApi
import com.tencent.bkrepo.opdata.repository.OpDeregisterServiceInstanceRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 微服务运营管理
 */
@Service
class OpServiceService @Autowired constructor(
    private val registryApi: RegistryApi,
    private val opDeregisterServiceInstanceRepository: OpDeregisterServiceInstanceRepository
) {
    /**
     * 获取服务列表
     */
    fun listServices(): List<ServiceInfo> {
        return registryApi.services()
    }

    /**
     * 获取服务的所有实例
     */
    fun instances(serviceName: String): List<InstanceInfo> {
        return registryApi.instances(serviceName)
    }

    fun instance(serviceName: String, instanceId: String): InstanceInfo {
        return registryApi.instanceInfo(serviceName, instanceId)
    }

    /**
     * 下线服务实例
     */
    @Transactional(rollbackFor = [Exception::class])
    fun downInstance(serviceName: String, instanceId: String): InstanceInfo {
        if (!opDeregisterServiceInstanceRepository.existsById(instanceId)) {
            val instanceInfo = instance(serviceName, instanceId)
            opDeregisterServiceInstanceRepository.insert(toModel(instanceInfo))
            return registryApi.deregister(serviceName, instanceId)
        } else {
            throw ErrorCodeException(CONFLICT, ServiceInstanceDeregisterConflict, arrayOf(serviceName, instanceId))
        }
    }

    /**
     * 删除注销的服务实例记录
     */
    fun deleteDownServiceInstance(serviceName: String, instanceId: String) {
        opDeregisterServiceInstanceRepository.deleteById(instanceId)
    }

    private fun toModel(instanceInfo: InstanceInfo): TOpDeregisterServiceInstance {
        return TOpDeregisterServiceInstance(
            instanceInfo.id,
            instanceInfo.host,
            instanceInfo.port
        )
    }
}
