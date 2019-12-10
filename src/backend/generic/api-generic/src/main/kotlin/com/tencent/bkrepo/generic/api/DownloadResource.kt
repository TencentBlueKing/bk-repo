package com.tencent.bkrepo.generic.api

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping

/**
 * 下载接口
 *
 * @author: carrypan
 * @date: 2019-09-28
 */
@Api("下载接口")
interface DownloadResource {

    @ApiOperation("文件下载")
    @GetMapping(DEFAULT_MAPPING_URI)
    fun download(@ArtifactPathVariable artifactInfo: GenericArtifactInfo)
}
