package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 用户查看节点列表页面
 *
 * @author: carrypan
 * @date: 2019-11-18
 */
@RequestMapping("/api/list")
interface UserListViewResource {

    @GetMapping(DEFAULT_MAPPING_URI)
    fun listNodeView(@ArtifactPathVariable artifactInfo: ArtifactInfo)

    @GetMapping
    fun listProjectView()

    @GetMapping("/{projectId}")
    fun listRepositoryView(@RequestAttribute userId: String, @PathVariable projectId: String)
}
