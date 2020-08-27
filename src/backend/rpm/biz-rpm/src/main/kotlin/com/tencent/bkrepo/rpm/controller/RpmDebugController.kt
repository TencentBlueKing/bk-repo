package com.tencent.bkrepo.rpm.controller

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import com.tencent.bkrepo.rpm.servcie.RpmDebugService
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
}