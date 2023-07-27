package com.tencent.bkrepo.npm.controller.service

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.npm.api.NpmClient
import com.tencent.bkrepo.npm.service.ServiceNpmClientService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceNpmClientController(
    private val service: ServiceNpmClientService
) : NpmClient {

    override fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        operator: String
    ): Response<Void> {
        service.deleteVersion(projectId,repoName,packageKey,version, SYSTEM_USER)
        return ResponseBuilder.success()
    }
}
