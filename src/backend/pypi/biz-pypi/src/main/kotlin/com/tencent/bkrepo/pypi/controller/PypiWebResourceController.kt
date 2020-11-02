package com.tencent.bkrepo.pypi.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.pypi.api.PypiWebResource
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.service.PypiWebService
import org.springframework.web.bind.annotation.RestController

@RestController
class PypiWebResourceController(
    private val pypiWebService: PypiWebService
) : PypiWebResource {
    override fun deletePackage(pypiArtifactInfo: PypiArtifactInfo, packageKey: String): Response<Void> {
        pypiWebService.deletePackage(pypiArtifactInfo, packageKey)
        return ResponseBuilder.success()
    }

    override fun deleteVersion(pypiArtifactInfo: PypiArtifactInfo, packageKey: String, version: String?): Response<Void> {
        pypiWebService.delete(pypiArtifactInfo, packageKey, version)
        return ResponseBuilder.success()
    }

    override fun artifactDetail(pypiArtifactInfo: PypiArtifactInfo, packageKey: String, version: String?): Response<Any?> {
        return ResponseBuilder.success(pypiWebService.artifactDetail(pypiArtifactInfo, packageKey, version))
    }
}
