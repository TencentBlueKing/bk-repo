package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.repository.pojo.experience.AppExperienceDetail
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceChangeLogRequest
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceList
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceRequest
import com.tencent.bkrepo.repository.pojo.experience.DevopsResponse
import com.tencent.bkrepo.repository.pojo.experience.PaginationExperienceChangeLog
import com.tencent.bkrepo.repository.pojo.experience.PaginationExperienceInstallPackages
import com.tencent.bkrepo.repository.service.experience.ExperienceService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/experience")
class UserExperienceController(
    private val experienceService: ExperienceService
) {

    @PostMapping("/list")
    fun listAppExperience(
        @RequestAttribute userId: String,
        @RequestBody request: AppExperienceRequest
    ): DevopsResponse<AppExperienceList> {
        return experienceService.list(userId, request)
    }

    @PostMapping("/detail/{experienceId}")
    fun detail(
        @RequestAttribute userId: String,
        @PathVariable experienceId: String,
        @RequestBody request: AppExperienceRequest
    ): DevopsResponse<AppExperienceDetail> {
        return experienceService.getExperienceDetail(userId, experienceId, request)
    }

    @PostMapping("/changelog/{experienceId}")
    fun changelog(
        @RequestAttribute userId: String,
        @PathVariable experienceId: String,
        @RequestBody request: AppExperienceChangeLogRequest
    ): DevopsResponse<PaginationExperienceChangeLog> {
        return experienceService.getExperienceChangeLog(userId, experienceId, request)
    }

    @PostMapping("/installPackages/{experienceId}")
    fun installPackages(
        @RequestAttribute userId: String,
        @PathVariable experienceId: String,
        @RequestBody request: AppExperienceRequest
    ): DevopsResponse<PaginationExperienceInstallPackages> {
        return experienceService.getExperienceInstallPackages(userId, experienceId, request)
    }
}
