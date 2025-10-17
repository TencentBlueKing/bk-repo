package com.tencent.bkrepo.media.job.controller

import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.media.common.pojo.transcode.TranscodeReportData
import com.tencent.bkrepo.media.job.pojo.MediaArtifactInfo
import com.tencent.bkrepo.media.job.service.TokenService
import com.tencent.bkrepo.media.job.service.TranscodeJobService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/user/media")
class UserJobController @Autowired constructor(
    private val tokenService: TokenService,
    private val transcodeJobService: TranscodeJobService
) {

    /**
     * 上报转码任务状态和信息
     */
    @PutMapping("/job/report/{projectId}/{repoName}/streams/**")
    fun jobReport(
        artifactInfo: MediaArtifactInfo,
        @RequestParam token: String,
        @RequestBody data: TranscodeReportData,
    ) {
        tokenService.validateToken(token, artifactInfo, TokenType.ALL)
        transcodeJobService.jobReport(artifactInfo, data)
    }
}