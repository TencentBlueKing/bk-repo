/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.controller.user

import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.operate.api.annotation.LogOperate
import com.tencent.bkrepo.common.security.constant.MS_AUTH_HEADER_SECURITY_TOKEN
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.service.ServiceAuthManager
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.job.pojo.JobDetail
import com.tencent.bkrepo.job.service.SystemJobService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/job")
@Principal(type = PrincipalType.ADMIN)
class UserJobController(
    val systemJobService: SystemJobService,
    val discoveryClient: DiscoveryClient,
    val serviceAuthManager: ServiceAuthManager
) {

    private val restTemplate = RestTemplate()

    private val timeoutInSecond:Int = 15

    private val jobServiceId:String = "bkrepo-job"

    @Value("\${spring.cloud.client.ip-address}")
    private val ipAddress:String = ""


    @GetMapping("/detail")
    @LogOperate(type = "JOB_LIST")
    fun detail(): Response<List<JobDetail>> {
        return ResponseBuilder.success(systemJobService.detail())
    }

    @PutMapping("/update/{name}")
    @LogOperate(type = "JOB_STATUS_UPDATE")
    fun update(@PathVariable name: String, enabled: Boolean, running: Boolean): Response<Boolean> {
        val instances = discoveryClient.getInstances(jobServiceId)
        return if (instances.size == 1) {
            ResponseBuilder.success(
                standaloneUpdateJob(name, enabled, running))
        } else {
            ResponseBuilder.success(
                multipleUpdateJob(name, enabled, running, instances)
            )
        }
    }

    private fun standaloneUpdateJob(name: String, enabled: Boolean, running: Boolean) : Boolean{
        return systemJobService.update(name, enabled, running)
    }

    private fun multipleUpdateJob(
        name: String,
        enabled: Boolean,
        running: Boolean,
        instances: List<ServiceInstance>
    ): Boolean {
        val path = "/service/job/update/$name"
        val requestBody: MutableMap<String, Boolean> = HashMap()
        requestBody["running"] = running
        requestBody["enabled"] = enabled
        val results : MutableList<Boolean> = ArrayList()
        val countDownLatch = CountDownLatch(instances.size)
        instances.forEach {
                instance ->
            if (instance.host.equals(ipAddress)) {
                results.add(systemJobService.update(name, enabled, running))
            } else {
                val targetUri =  instance.uri
                val url = "$targetUri$path"
                val headers = HttpHeaders()
                headers.add(MS_AUTH_HEADER_SECURITY_TOKEN, serviceAuthManager.getSecurityToken())
                headers.add(MS_AUTH_HEADER_UID, SYSTEM_USER)
                val httpEntity = HttpEntity<Any>(requestBody,headers)
                val response = restTemplate.exchange(url, HttpMethod.PUT, httpEntity, Response::class.java)
                results.add(response.statusCode == HttpStatus.OK)
                if (response.statusCode != HttpStatus.OK) {
                    logger.error(
                        "Instance has error, job:$name change running to $running, change enable to $enabled fail")
                }
            }
            countDownLatch.countDown()
        }
        countDownLatch.await(timeoutInSecond.toLong(), TimeUnit.SECONDS)
        return results.size == instances.size && !results.contains(false)
    }

    @PutMapping("/stop")
    fun stop(
        @RequestParam(required = false) name: String?,
        @RequestParam maxWaitTime: Long = 0,
        @RequestParam failover: Boolean,
    ): Response<Void> {
        systemJobService.stop(name, maxWaitTime, failover)
        return ResponseBuilder.success()
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(UserJobController::class.java)
    }
}
