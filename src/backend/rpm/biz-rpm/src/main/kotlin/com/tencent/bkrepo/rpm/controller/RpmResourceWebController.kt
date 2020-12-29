package com.tencent.bkrepo.rpm.controller

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import com.tencent.bkrepo.rpm.servcie.RpmWebService
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
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

    @GetMapping(RpmArtifactInfo.RPM_EXT_LIST)
    fun list(
            @ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo,
            @ApiParam(value = "当前页", required = true, defaultValue = "0")
            @RequestParam page: Int = 0,
            @ApiParam(value = "分页大小", required = true, defaultValue = "20")
            @RequestParam size: Int = 20
    ): Page<String> {
        return rpmWebService.extList(rpmArtifactInfo, page, size)
    }
}
