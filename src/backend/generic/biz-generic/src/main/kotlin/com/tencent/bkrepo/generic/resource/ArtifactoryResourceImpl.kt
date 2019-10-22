package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.generic.api.ArtifactoryResource
import com.tencent.bkrepo.generic.service.ArtifactoryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.InputStreamResource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
class ArtifactoryResourceImpl @Autowired constructor(
    private val artifactoryService: ArtifactoryService
) : ArtifactoryResource{
    override fun upload(userId: String, projectId: String, repoName: String, fullPath: String, request: HttpServletRequest): Response<Void> {
        artifactoryService.
    }

    override fun download(userId: String, projectId: String, repoName: String, fullPath: String, response: HttpServletResponse): ResponseEntity<InputStreamResource> {

    }

    override fun listFile(userId: String, projectId: String, repoName: String, fullPath: String, response: HttpServletResponse){

    }
}
