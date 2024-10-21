package com.tencent.bkrepo.common.metadata.service.packages

import com.tencent.bkrepo.repository.pojo.software.ProjectPackageOverview

interface PackageStatisticsService {

    /**
     * 包搜索总览
     */
    fun packageOverview(repoType: String, projectId: String, packageName: String?): List<ProjectPackageOverview>

}
