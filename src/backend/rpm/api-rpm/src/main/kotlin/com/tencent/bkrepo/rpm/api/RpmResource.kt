package com.tencent.bkrepo.rpm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo.Companion.RPM_DEPLOY
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping

@Api("rpm 接口")
interface RpmResource {

    @ApiOperation("rpm deploy")
    @PutMapping(RPM_DEPLOY, produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
    fun deploy(@ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo, artifactFile: ArtifactFile)

    @ApiOperation("rpm install")
    @GetMapping(RPM_DEPLOY)
    fun install(@ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo)
}
