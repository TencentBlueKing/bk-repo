package com.tencent.bkrepo.rpm.controller

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.rpm.api.RpmResource
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import com.tencent.bkrepo.rpm.servcie.RpmService
import org.springframework.web.bind.annotation.RestController

@RestController
class RpmResourceController(
    private val rpmService: RpmService
) : RpmResource {
    override fun deploy(rpmArtifactInfo: RpmArtifactInfo, artifactFile: ArtifactFile) {
        rpmService.deploy(rpmArtifactInfo, artifactFile)
    }

    override fun install(rpmArtifactInfo: RpmArtifactInfo) {
        rpmService.install(rpmArtifactInfo)
    }

    override fun addGroups(rpmArtifactInfo: RpmArtifactInfo, groups: MutableSet<String>) {
        rpmService.addGroups(rpmArtifactInfo, groups)
    }

    override fun deleteGroups(rpmArtifactInfo: RpmArtifactInfo, groups: MutableSet<String>) {
        rpmService.deleteGroups(rpmArtifactInfo, groups)
    }
}
