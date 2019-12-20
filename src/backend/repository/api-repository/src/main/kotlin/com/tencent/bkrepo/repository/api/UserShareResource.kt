package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.repository.pojo.share.ShareRecordCreateRequest
import com.tencent.bkrepo.repository.pojo.share.ShareRecordInfo
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 用户分享接口
 *
 * @author: carrypan
 * @date: 2019/12/20
 */
@RequestMapping("/api/share")
interface UserShareResource {

    @ApiOperation("创建分享链接")
    @PostMapping(DEFAULT_MAPPING_URI)
    fun share(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestBody shareRecordCreateRequest: ShareRecordCreateRequest
    ): Response<ShareRecordInfo>

    @ApiOperation("下载分享文件")
    @GetMapping(DEFAULT_MAPPING_URI)
    fun download(
        @RequestAttribute userId: String,
        @RequestParam token: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo
    )

}