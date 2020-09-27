package com.tencent.bkrepo.auth.pojo

data class Action(
    val id: String,
    val related_resource_types: List<RelatedResourceTypes>?
)