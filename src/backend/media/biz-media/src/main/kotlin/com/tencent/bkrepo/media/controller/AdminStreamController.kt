package com.tencent.bkrepo.media.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.media.common.model.TMediaLiveConfig
import com.tencent.bkrepo.media.service.LiveConfigService
import com.tencent.bkrepo.media.stream.StreamManger
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 管理员流控制器
 * */
@RestController
@RequestMapping("/api/admin/stream")
@Principal(PrincipalType.ADMIN)
class AdminStreamController(
    private val streamManger: StreamManger,
    private val liveConfigService: LiveConfigService,
) {
    @PutMapping("/shutdown")
    fun shutdown() {
        streamManger.close(System.currentTimeMillis())
    }

    /**
     * 创建直播模式配置
     * projectId、userId、workspaceId 三选一填入即可
     */
    @PostMapping("/live/config")
    fun createLiveConfig(
        @RequestAttribute userId: String,
        @RequestParam(required = false) projectId: String?,
        @RequestParam(required = false) configUserId: String?,
        @RequestParam(required = false) workspaceId: String?,
        @RequestParam(required = false, defaultValue = "true") enabled: Boolean,
    ): Response<TMediaLiveConfig> {
        return ResponseBuilder.success(
            liveConfigService.createConfig(projectId, configUserId, workspaceId, enabled, userId)
        )
    }

    /**
     * 更新直播模式配置
     */
    @PutMapping("/live/config")
    fun updateLiveConfig(
        @RequestAttribute userId: String,
        @RequestParam id: String,
        @RequestParam(required = false) projectId: String?,
        @RequestParam(required = false) configUserId: String?,
        @RequestParam(required = false) workspaceId: String?,
        @RequestParam enabled: Boolean,
    ): Response<Void> {
        liveConfigService.updateConfig(id, projectId, configUserId, workspaceId, enabled, userId)
        return ResponseBuilder.success()
    }

    /**
     * 删除直播模式配置
     */
    @DeleteMapping("/live/config")
    fun deleteLiveConfig(
        @RequestParam id: String,
    ): Response<Void> {
        liveConfigService.deleteConfig(id)
        return ResponseBuilder.success()
    }

    /**
     * 查询单个直播模式配置
     * projectId、userId、workspaceId 三选一传入即可
     */
    @GetMapping("/live/config")
    fun getLiveConfig(
        @RequestParam(required = false) projectId: String?,
        @RequestParam(required = false) configUserId: String?,
        @RequestParam(required = false) workspaceId: String?,
    ): Response<TMediaLiveConfig?> {
        return ResponseBuilder.success(liveConfigService.getConfig(projectId, configUserId, workspaceId))
    }

    /**
     * 查询所有直播模式配置
     */
    @GetMapping("/live/config/list")
    fun listAllLiveConfig(): Response<List<TMediaLiveConfig>> {
        return ResponseBuilder.success(liveConfigService.listAll())
    }
}
