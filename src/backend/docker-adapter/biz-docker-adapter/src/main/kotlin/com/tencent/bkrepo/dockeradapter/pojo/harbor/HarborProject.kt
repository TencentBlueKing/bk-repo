package com.tencent.bkrepo.dockeradapter.pojo.harbor

import com.fasterxml.jackson.annotation.JsonProperty

data class HarborProject(
    @JsonProperty("project_id")
    val projectId: Int,
    @JsonProperty("name")
    val name: String,
    @JsonProperty("creation_time")
    var createTime: String,
    @JsonProperty("update_time")
    var updateTime: String,
    @JsonProperty("repo_count")
    val repoCount: Int
)