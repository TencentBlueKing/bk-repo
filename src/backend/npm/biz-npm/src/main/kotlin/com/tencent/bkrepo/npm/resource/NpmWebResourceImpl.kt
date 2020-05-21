package com.tencent.bkrepo.npm.resource

import com.tencent.bkrepo.npm.api.NpmWebResource
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.PackageInfoResponse
import com.tencent.bkrepo.npm.service.NpmWebService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class NpmWebResourceImpl : NpmWebResource {

    @Autowired
    private lateinit var npmWebService: NpmWebService

    override fun getPackageInfo(artifactInfo: NpmArtifactInfo): PackageInfoResponse {
        return npmWebService.getPackageInfo(artifactInfo)
    }
}
