package com.tencent.bkrepo.dockerapi.pojo.harbor

import com.fasterxml.jackson.annotation.JsonProperty

data class HarborRepo(
    @JsonProperty("name")
    val name: String,
    @JsonProperty("project_id")
    val projectId: Int,
    @JsonProperty("description")
    val description: Int,
    @JsonProperty("pull_count")
    val pullCount: Int,
    @JsonProperty("star_count")
    val starCount: Int,
    @JsonProperty("tags_count")
    val tagsCount: Int,
    @JsonProperty("creation_time")
    val createTime: String,
    @JsonProperty("update_time")
    val updateTime: String
)