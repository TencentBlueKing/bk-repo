package com.tencent.bkrepo.rpm.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import com.tencent.bkrepo.rpm.servcie.RpmDebugService
import com.tencent.bkrepo.rpm.servcie.RpmService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * rpm 纠错接口
 */

@RestController
class RpmDebugController(
    private val rpmDebugService: RpmDebugService
) {

    @GetMapping(RpmArtifactInfo.RPM_DEBUG_FLUSH)
    fun flushRepomd(@ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo) {
        rpmDebugService.flushRepomd(rpmArtifactInfo)
    }

    @GetMapping(RpmArtifactInfo.RPM_DEBUG_ALL_FLUSH)
    fun flushAllRepomd(@ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo) {
        rpmDebugService.flushAllRepomd(rpmArtifactInfo)
    }

    @DeleteMapping(RpmArtifactInfo.RPM)
    fun delete(@ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo) {
        rpmDebugService.delete(rpmArtifactInfo)
    }
}
