package com.tencent.bkrepo.rpm.controller

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import com.tencent.bkrepo.rpm.servcie.RpmWebService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RestController

/**
 * rpm 仓库 非标准接口
 */
@RestController
class RpmResourceWebController(
        private val rpmWebService: RpmWebService
) {
    @DeleteMapping(RpmArtifactInfo.RPM, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun delete(@ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo) {
        rpmWebService.delete(rpmArtifactInfo)
    }
}