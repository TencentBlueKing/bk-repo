package com.tencent.bkrepo.helm.controller.service

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.helm.api.HelmPackageClient
import com.tencent.bkrepo.helm.service.ServiceHelmClientService
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceHelmClientController(
    private val service: ServiceHelmClientService
) : HelmPackageClient {
    override fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        operator: String
    ): Response<Void> {
        service.deleteVersion(projectId, repoName, packageKey, version, operator)
        return ResponseBuilder.success()
    }
}
