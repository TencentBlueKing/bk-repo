package com.tencent.bkrepo.npm.resource

import com.tencent.bkrepo.npm.api.NpmFixToolResource
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.fixtool.DateTimeFormatResponse
import com.tencent.bkrepo.npm.pojo.fixtool.PackageMetadataFixResponse
import com.tencent.bkrepo.npm.service.NpmFixToolService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class NpmFixToolResourceImpl @Autowired constructor(
    private val npmFixToolService: NpmFixToolService
) : NpmFixToolResource {

    override fun fixDateFormat(artifactInfo: NpmArtifactInfo, pkgName: String): DateTimeFormatResponse {
        return npmFixToolService.fixDateFormat(artifactInfo, pkgName)
    }

    override fun fixPackageSizeField(artifactInfo: NpmArtifactInfo): PackageMetadataFixResponse {
        return npmFixToolService.fixPackageSizeField(artifactInfo)
    }
}
