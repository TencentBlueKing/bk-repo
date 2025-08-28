package com.tencent.bkrepo.repository.pojo.experience

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(title = "版本体验-体验列表")
data class AppExperienceList(
    @get:Schema(description = "内部体验列表")
    val privateExperiences: List<AppExperience>? = emptyList(),
    @get:Schema(description = "公开体验列表")
    val publicExperiences: List<AppExperience>? = emptyList(),
    @get:Schema(description = "红点个数")
    val redPointCount: Long? = 0
)