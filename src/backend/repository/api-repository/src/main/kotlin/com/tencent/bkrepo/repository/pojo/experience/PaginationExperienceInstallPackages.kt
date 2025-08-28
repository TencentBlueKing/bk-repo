package com.tencent.bkrepo.repository.pojo.experience

import io.swagger.v3.oas.annotations.media.Schema

data class PaginationExperienceInstallPackages(
    @get:Schema(title = "总记录行数")
    val count: Long? = null,

    @get:Schema(title = "是否有下一页")
    val hasNext: Boolean,

    @get:Schema(title = "数据")
    val records: List<AppExperienceInstallPackage>
)
