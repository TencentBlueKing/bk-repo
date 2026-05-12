package com.tencent.bkrepo.media.job.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.media.common.model.TMediaTranscodeJobConfig
import com.tencent.bkrepo.media.job.service.TranscodeJobConfigService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 转码任务配置管理接口
 */
@RestController
@RequestMapping("/api/media/job/config")
@Principal(PrincipalType.ADMIN)
class AdminTranscodeJobConfigController(
    private val transcodeJobConfigService: TranscodeJobConfigService,
) {

    /**
     * 创建转码任务配置
     */
    @PostMapping
    fun createConfig(
        @RequestParam image: String,
        @RequestParam(required = false) projectId: String?,
        @RequestParam(required = false) maxJobCount: Int?,
        @RequestParam(required = false) resource: String?,
        @RequestParam(required = false) cosConfigMapName: String?,
    ): Response<TMediaTranscodeJobConfig> {
        return ResponseBuilder.success(
            transcodeJobConfigService.createConfig(projectId, maxJobCount, image, resource, cosConfigMapName)
        )
    }

    /**
     * 更新转码任务配置
     */
    @PutMapping
    fun updateConfig(
        @RequestParam id: String,
        @RequestParam(required = false) projectId: String?,
        @RequestParam(required = false) maxJobCount: Int?,
        @RequestParam(required = false) image: String?,
        @RequestParam(required = false) resource: String?,
        @RequestParam(required = false) cosConfigMapName: String?,
    ): Response<Void> {
        transcodeJobConfigService.updateConfig(id, projectId, maxJobCount, image, resource, cosConfigMapName)
        return ResponseBuilder.success()
    }

    /**
     * 删除转码任务配置
     */
    @DeleteMapping
    fun deleteConfig(
        @RequestParam id: String,
    ): Response<Void> {
        transcodeJobConfigService.deleteConfig(id)
        return ResponseBuilder.success()
    }

    /**
     * 查询单个配置（按项目ID）
     */
    @GetMapping
    fun getConfig(
        @RequestParam(required = false) projectId: String?,
    ): Response<TMediaTranscodeJobConfig?> {
        return ResponseBuilder.success(transcodeJobConfigService.getConfig(projectId))
    }

    /**
     * 查询所有配置
     */
    @GetMapping("/list")
    fun listAllConfig(): Response<List<TMediaTranscodeJobConfig>> {
        return ResponseBuilder.success(transcodeJobConfigService.listAll())
    }
}
