package com.tencent.bkrepo.rpm.resource

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.rpm.api.RpmResource
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import com.tencent.bkrepo.rpm.servcie.RpmService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class RpmResourceImpl(
    @Autowired
    private val rpmService: RpmService
) : RpmResource {
    override fun deploy(rpmArtifactInfo: RpmArtifactInfo, artifactFile: ArtifactFile) {
        rpmService.deploy(rpmArtifactInfo, artifactFile)
    }

    override fun install(rpmArtifactInfo: RpmArtifactInfo) {
        rpmService.install(rpmArtifactInfo)
    }
}
