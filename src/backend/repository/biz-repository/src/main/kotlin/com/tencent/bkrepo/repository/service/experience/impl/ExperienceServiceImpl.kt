package com.tencent.bkrepo.repository.service.experience.impl

import com.tencent.bkrepo.repository.pojo.experience.AppExperienceDetail
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceChangeLogRequest
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceList
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceRequest
import com.tencent.bkrepo.repository.pojo.experience.DevopsResponse
import com.tencent.bkrepo.repository.pojo.experience.PaginationExperienceChangeLog
import com.tencent.bkrepo.repository.pojo.experience.PaginationExperienceInstallPackages
import com.tencent.bkrepo.repository.service.experience.CIExperienceService
import com.tencent.bkrepo.repository.service.experience.ExperienceService
import org.springframework.stereotype.Service

@Service
class ExperienceServiceImpl(
    private val ciExperienceService: CIExperienceService
) : ExperienceService {

    override fun list(userId: String, request: AppExperienceRequest): DevopsResponse<AppExperienceList> =
        ciExperienceService.getAppExperiences(userId, request)

    override fun getExperienceDetail(
        userId: String,
        experienceId: String,
        request: AppExperienceRequest
    ): DevopsResponse<AppExperienceDetail> =
        ciExperienceService.getAppExperienceDetail(userId, experienceId, request)

    override fun getExperienceChangeLog(
        userId: String,
        experienceId: String,
        request: AppExperienceChangeLogRequest
    ): DevopsResponse<PaginationExperienceChangeLog> =
        ciExperienceService.getAppExperienceChangeLog(userId, experienceId, request)

    override fun getExperienceInstallPackages(
        userId: String,
        experienceId: String,
        request: AppExperienceRequest
    ): DevopsResponse<PaginationExperienceInstallPackages> =
        ciExperienceService.getAppExperienceInstallPackages(userId, experienceId, request)

}
