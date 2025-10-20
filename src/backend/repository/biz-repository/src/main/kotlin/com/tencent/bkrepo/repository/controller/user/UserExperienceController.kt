package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceChangeLogRequest
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceHeader
import com.tencent.bkrepo.repository.service.experience.CIExperienceService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/experience")
@Principal(PrincipalType.GENERAL)
class UserExperienceController(
    private val ciExperienceService: CIExperienceService
) {

    @PostMapping("/list")
    fun listAppExperience(
        @RequestAttribute userId: String,
        @RequestBody request: AppExperienceHeader
    ) {
        ciExperienceService.getAppExperiences(userId, request)
    }

    @PostMapping("/detail/{experienceId}")
    fun detail(
        @RequestAttribute userId: String,
        @PathVariable experienceId: String,
        @RequestBody request: AppExperienceHeader
    ) {
        ciExperienceService.getAppExperienceDetail(userId, experienceId, request)
    }

    @PostMapping("/changelog/{experienceId}")
    fun changelog(
        @RequestAttribute userId: String,
        @PathVariable experienceId: String,
        @RequestBody request: AppExperienceChangeLogRequest
    ) {
        ciExperienceService.getAppExperienceChangeLog(userId, experienceId, request)
    }

    @PostMapping("/installPackages/{experienceId}")
    fun installPackages(
        @RequestAttribute userId: String,
        @PathVariable experienceId: String,
        @RequestBody request: AppExperienceHeader
    ) {
        ciExperienceService.getAppExperienceInstallPackages(userId, experienceId, request)
    }

    @PostMapping("/downloadUrl/{experienceId}")
    fun downloadUrl(
        @RequestAttribute userId: String,
        @PathVariable experienceId: String,
        @RequestBody request: AppExperienceHeader
    ) {
        ciExperienceService.getAppExperienceDownloadUrl(userId, experienceId, request)
    }
}
