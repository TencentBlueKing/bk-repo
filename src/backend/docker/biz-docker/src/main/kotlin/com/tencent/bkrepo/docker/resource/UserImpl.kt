package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.docker.api.User
import com.tencent.bkrepo.docker.context.RequestContext
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
        val artifactName = PathUtil.userArtifactName(request, projectId, repoName, tag)
        val uId = UserUtil.getContextUserId(userId)
        val context = RequestContext(uId, projectId, repoName, artifactName)
        val result = dockerRepo.getManifestString(context, tag)
        return ResponseBuilder.success(result)
    }

    override fun getLayer(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String,
        id: String
    ): ResponseEntity<Any> {
        val uId = UserUtil.getContextUserId(userId)
        val artifactName = PathUtil.layerArtifactName(request, projectId, repoName, id)
        val context = RequestContext(uId, projectId, repoName, artifactName)
        return dockerRepo.buildLayerResponse(context, id)
    }

    override fun getRepo(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String
    ): Response<List<String>> {
        val uId = UserUtil.getContextUserId(userId)
        val context = RequestContext(uId, projectId, repoName, "")
        val result = dockerRepo.getRepoList(context)
        return ResponseBuilder.success(result)
    }

    override fun getRepoTag(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String
    ): Response<Map<String, String>> {
        val uId = UserUtil.getContextUserId(userId)
        val artifactName = PathUtil.tagArtifactName(request, projectId, repoName)
        val context = RequestContext(uId, projectId, repoName, artifactName)
        val result = dockerRepo.getRepoTagList(context)
        return ResponseBuilder.success(result)
    }
}
