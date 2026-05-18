package com.tencent.bkrepo.huggingface.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.huggingface.artifact.HuggingfaceArtifactInfo
import com.tencent.bkrepo.huggingface.pojo.GitRefs
import com.tencent.bkrepo.huggingface.pojo.user.UserTagCreateRequest
import com.tencent.bkrepo.huggingface.service.HfTagService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * HuggingFace CLI tag 命令相关接口
 * 支持创建、删除和列出 tag
 */
@RestController
class HfTagController(
    private val hfTagService: HfTagService,
) {

    /**
     * 创建 tag
     * 对应命令: hf repo tag create <repo_id> <tag_name> --revision <revision>
     */
    @PostMapping("/{projectId}/{repoName}/api/{type}s/{organization}/{name}/tag/{revision}")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun createTag(
        @ArtifactPathVariable artifactInfo: HuggingfaceArtifactInfo,
        @RequestBody request: UserTagCreateRequest
    ) {
        hfTagService.createTag(artifactInfo, request)
    }

    /**
     * 删除 tag
     * 对应命令: hf repo tag delete <repo_id> <tag_name>
     */
    @DeleteMapping("/{projectId}/{repoName}/api/{type}s/{organization}/{name}/tag/{tag}")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun deleteTag(
        @ArtifactPathVariable artifactInfo: HuggingfaceArtifactInfo,
        @PathVariable tag: String
    ) {
        hfTagService.deleteTag(artifactInfo, tag)
    }

    /**
     * 列出所有 refs
     * 对应命令: hf repo tag list <repo_id>
     */
    @GetMapping("/{projectId}/{repoName}/api/{type}s/{organization}/{name}/refs")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun listRefs(
        @ArtifactPathVariable artifactInfo: HuggingfaceArtifactInfo,
    ): GitRefs {
        return hfTagService.listRefs(artifactInfo)
    }
}