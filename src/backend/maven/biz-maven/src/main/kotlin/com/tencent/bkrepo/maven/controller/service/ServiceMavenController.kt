package com.tencent.bkrepo.maven.controller.service

import com.tencent.bkrepo.maven.api.MavenClient
import com.tencent.bkrepo.maven.service.MavenDeleteService
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceMavenController(
    private val mavenDeleteService: MavenDeleteService
) : MavenClient {

    override fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        operator: String
    ): Boolean {
        return mavenDeleteService.deleteVersion(projectId, repoName, packageKey, version, operator)
    }
}
