package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceDetail
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceInstallPackage
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceChangeLogRequest
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceList
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceRequest
import com.tencent.bkrepo.repository.pojo.experience.PaginationExperienceChangeLog
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
    ): Response<AppExperienceList> {
        return ResponseBuilder.success(experienceService.list(userId, request))
    }

    @PostMapping("/detail/{experienceId}")
    fun detail(
        @RequestAttribute userId: String,
        @PathVariable experienceId: String,
        @RequestBody request: AppExperienceRequest
    ): Response<AppExperienceDetail?> {
        return ResponseBuilder.success(
            runCatching { experienceService.getExperienceDetail(userId, experienceId, request) }
                .getOrNull()
        )
    }

    @PostMapping("/changelog/{experienceId}")
    fun changelog(
        @RequestAttribute userId: String,
        @PathVariable experienceId: String,
        @RequestBody request: AppExperienceChangeLogRequest
    ): Response<PaginationExperienceChangeLog> {
        return ResponseBuilder.success(
            runCatching { experienceService.getExperienceChangeLog(userId, experienceId, request) }
                .getOrDefault(PaginationExperienceChangeLog(0, false, emptyList()))
        )
    }

    @PostMapping("/installPackages/{experienceId}")
    fun installPackages(
        @RequestAttribute userId: String,
        @PathVariable experienceId: String,
        @RequestBody request: AppExperienceRequest
    ): Response<List<AppExperienceInstallPackage>> =
        ResponseBuilder.success(
            experienceService.getExperienceInstallPackages(userId, experienceId, request)
        )
}
