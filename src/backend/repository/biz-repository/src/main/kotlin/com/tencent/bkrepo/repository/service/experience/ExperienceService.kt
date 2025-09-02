package com.tencent.bkrepo.repository.service.experience

import com.tencent.bkrepo.repository.pojo.experience.AppExperienceChangeLogRequest
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceRequest

interface ExperienceService {
    /**
     * 根据用户和平台获取版本体验列表
     */
    fun list(userId: String, request: AppExperienceRequest): String

    /**
     * 根据用户和体验 ID 获取体验详情
     */
    fun getExperienceDetail(userId: String,
                            experienceId: String,
                            request: AppExperienceRequest
    ): String

    /**
     * 根据用户和体验 ID 获取体验变更日志
     */
    fun getExperienceChangeLog(
        userId: String,
        experienceId: String,
        request: AppExperienceChangeLogRequest
    ): String

    /**
     * 根据用户和体验 ID 获取体验安装包
     */
    fun getExperienceInstallPackages(
        userId: String,
        experienceId: String,
        request: AppExperienceRequest
    ): String
}
