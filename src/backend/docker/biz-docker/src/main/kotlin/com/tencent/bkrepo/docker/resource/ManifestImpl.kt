package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.docker.api.Manifest
import com.tencent.bkrepo.docker.constant.MANIFEST_PATTERN
import com.tencent.bkrepo.docker.service.DockerV2LocalRepoService
import com.tencent.bkrepo.docker.util.PathUtil
import com.tencent.bkrepo.docker.util.UserUtil
import javax.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * 元数据服务接口实现类
 *
 * @author: owenlxu
 * @date: 2019-10-03
 */

// ManifestImpl validates and impl the manifest interface
@RestController
class ManifestImpl @Autowired constructor(val dockerRepo: DockerV2LocalRepoService) : Manifest {

    override fun putManifest(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String,
        tag: String,
        contentType: String
    ): ResponseEntity<Any> {
        dockerRepo.userId = UserUtil.getContextUserId(userId)
        val name = PathUtil.artifactName(request, MANIFEST_PATTERN, projectId, repoName)
        return dockerRepo.uploadManifest(projectId, repoName, name, tag, contentType, request.inputStream)
    }

    override fun getManifest(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String,
        reference: String
    ): ResponseEntity<Any> {
        dockerRepo.userId = UserUtil.getContextUserId(userId)
        val name = PathUtil.artifactName(request, MANIFEST_PATTERN, projectId, repoName)
        return dockerRepo.getManifest(projectId, repoName, name, reference)
    }

    override fun existManifest(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String,
        reference: String
    ): ResponseEntity<Any> {
        dockerRepo.userId = UserUtil.getContextUserId(userId)
        val name = PathUtil.artifactName(request, MANIFEST_PATTERN, projectId, repoName)
        return dockerRepo.getManifest(projectId, repoName, name, reference)
    }
}
