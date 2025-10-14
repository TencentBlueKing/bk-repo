
/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent. All rights reserved.
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

package com.tencent.bkrepo.opdata.registry.spring

import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.opdata.message.OpDataMessageCode
import com.tencent.bkrepo.opdata.pojo.registry.InstanceInfo
import com.tencent.bkrepo.opdata.pojo.registry.InstanceStatus
import com.tencent.bkrepo.opdata.pojo.registry.ServiceInfo
import com.tencent.bkrepo.opdata.registry.RegistryClient
import org.slf4j.LoggerFactory
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.DiscoveryClient

/**
 * discoveryClient仅只能查看，无法做任何操作
 */
class SpringCloudServiceDiscovery(
  private val discoveryClient: DiscoveryClient,
  private val podLabelConfig: PodLabelConfig
): RegistryClient {

  data class ServiceDetails(
    val serviceName: String,
    var details: List<ServiceInstance>,
  )

  override fun services(): List<ServiceInfo> {
    val details = discoveryClient.services.map { ServiceDetails(
      serviceName = it,
      details =  discoveryClient.getInstances(it)
    )}
    val targetDetails = matchService(details)
    logger.info("targetDetails is $targetDetails")
    return targetDetails.map { service->
       ServiceInfo(
         name = service.serviceName,
         instances = service.details.map {
           buildInfo(it, service.serviceName)
         }
       )
     }
  }

  override fun instances(serviceName: String): List<InstanceInfo> {
    return matchInstance(serviceName).map { buildInfo(it, serviceName)
    }
  }

  private fun matchService( details: List<ServiceDetails>): List<ServiceDetails> {
    var targetDetails = details
    logger.info("details is $details")
    if (hasLabelConfig()) {
      targetDetails = details.filter {
        it.details.any { serviceInstance ->
          serviceInstance.metadata[podLabelConfig.labelName].equals(podLabelConfig.labelValue) }
      }
    }
    return targetDetails
  }

  private fun matchInstance(serviceName: String) : List<ServiceInstance> {
    val instances = discoveryClient.getInstances(serviceName)
    var matchResult = true
    if (hasLabelConfig()) {
      matchResult = instances.any { serviceInstance ->
        serviceInstance.metadata[podLabelConfig.labelName].equals(podLabelConfig.labelValue) }
    }
    return if (matchResult) instances else emptyList()

  }

  private fun hasLabelConfig(): Boolean {
    logger.info("labelName is " + podLabelConfig.labelName)
    logger.info("labelValue is " + podLabelConfig.labelValue)
    return podLabelConfig.labelValue.isNotBlank() && podLabelConfig.labelName.isNotBlank()
  }

  private fun buildInfo(serviceInstance: ServiceInstance, serviceName: String): InstanceInfo {
    with(serviceInstance) {
      return InstanceInfo(
        id = serviceId,
        serviceName = serviceName,
        host = host,
        port = port,
        status = InstanceStatus.RUNNING,
      )
    }
  }

  override fun deregister(serviceName: String, instanceId: String): InstanceInfo {
    throw SystemErrorException(OpDataMessageCode.NOT_SUPPORT)
  }

  override fun instanceInfo(serviceName: String, instanceId: String): InstanceInfo {
    val info = discoveryClient.getInstances(serviceName).first{ it.instanceId.equals(instanceId)}
    return buildInfo(info, serviceName)
  }

  override fun maintenance(serviceName: String, instanceId: String, enable: Boolean): InstanceInfo {
    throw SystemErrorException(OpDataMessageCode.NOT_SUPPORT)
  }

  companion object {
    private val logger = LoggerFactory.getLogger(SpringCloudServiceDiscovery::class.java)
  }

}