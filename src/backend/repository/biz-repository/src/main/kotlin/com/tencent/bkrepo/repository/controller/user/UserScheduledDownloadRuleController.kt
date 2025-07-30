package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.schedule.ScheduledDownloadRule
import com.tencent.bkrepo.repository.pojo.schedule.UserScheduledDownloadRuleCreateRequest
import com.tencent.bkrepo.repository.pojo.schedule.UserScheduledDownloadRuleQueryRequest
import com.tencent.bkrepo.repository.pojo.schedule.UserScheduledDownloadRuleUpdateRequest
import com.tencent.bkrepo.repository.service.schedule.ScheduledDownloadRuleService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "预约下载接口")
@RestController
@RequestMapping("/api/schedule/rule")
class UserScheduledDownloadRuleController(
    private val scheduledDownloadRuleService: ScheduledDownloadRuleService,
) {
    @Operation(summary = "创建预约下载规则")
    @PostMapping
    fun create(@RequestBody request: UserScheduledDownloadRuleCreateRequest): Response<ScheduledDownloadRule> {
        request.operator = SecurityUtils.getUserId()
        return ResponseBuilder.success(scheduledDownloadRuleService.create(request))
    }

    @Operation(summary = "删除预约下载规则")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): Response<Void> {
        scheduledDownloadRuleService.remove(id, SecurityUtils.getUserId())
        return ResponseBuilder.success()
    }

    @Operation(summary = "更新预约下载规则")
    @PutMapping
    fun update(@RequestBody request: UserScheduledDownloadRuleUpdateRequest): Response<ScheduledDownloadRule> {
        request.operator = SecurityUtils.getUserId()
        return ResponseBuilder.success(scheduledDownloadRuleService.update(request))
    }

    @Operation(summary = "查询项目级预约下载规则")
    @PostMapping("/project/query")
    fun projectRules(@RequestBody request: UserScheduledDownloadRuleQueryRequest): Response<Page<ScheduledDownloadRule>> {
        request.operator = SecurityUtils.getUserId()
        return ResponseBuilder.success(scheduledDownloadRuleService.projectRules(request))
    }

    @Operation(summary = "查询对当前用户生效的预约下载规则，包含项目级与用户自身创建的规则")
    @PostMapping("/query")
    fun rules(@RequestBody request: UserScheduledDownloadRuleQueryRequest): Response<Page<ScheduledDownloadRule>> {
        request.operator = SecurityUtils.getUserId()
        return ResponseBuilder.success(scheduledDownloadRuleService.rules(request))
    }
}
