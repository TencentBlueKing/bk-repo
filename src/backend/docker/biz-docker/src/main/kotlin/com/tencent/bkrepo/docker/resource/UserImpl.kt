package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.docker.api.User
import com.tencent.bkrepo.docker.model.DockerBasicPath
import com.tencent.bkrepo.docker.service.DockerV2LocalRepoService
import com.tencent.bkrepo.docker.util.PathUtil
import com.tencent.bkrepo.docker.util.UserUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
class UserImpl @Autowired constructor(val dockerRepo: DockerV2LocalRepoService) : User {

    override fun getManifest(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String,
        tag: String
    ): Response<String> {
        dockerRepo.userId = UserUtil.getContextUserId(userId)
        val imageName = PathUtil.userArtifactName(request, projectId, repoName, tag)
        val result = dockerRepo.getManifestString(DockerBasicPath(projectId, repoName, imageName), tag)
        return ResponseBuilder.success(result)
    }

    override fun getLayer(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String,
        id: String
    ): ResponseEntity<Any> {
        dockerRepo.userId = UserUtil.getContextUserId(userId)
        val imageName = PathUtil.layerArtifactName(request, projectId, repoName, id)
        return dockerRepo.buildLayerResponse(projectId, repoName, imageName, id)
    }

    override fun getRepo(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String
    ): Response<List<String>> {
        dockerRepo.userId = UserUtil.getContextUserId(userId)
        val result = dockerRepo.getRepoList(projectId, repoName)
        return ResponseBuilder.success(result)
    }

    override fun getRepoTag(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String
    ): Response<Map<String, String>> {
        dockerRepo.userId = UserUtil.getContextUserId(userId)
        val imageName = PathUtil.tagArtifactName(request, projectId, repoName)
        val result = dockerRepo.getRepoTagList(projectId, repoName, imageName)
        return ResponseBuilder.success(result)
    }
}
