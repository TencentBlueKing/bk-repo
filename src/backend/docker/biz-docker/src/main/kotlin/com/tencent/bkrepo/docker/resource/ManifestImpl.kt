package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.docker.api.Manifest
import com.tencent.bkrepo.docker.constant.MANIFEST_PATTERN
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.service.DockerV2LocalRepoService
import com.tencent.bkrepo.docker.util.PathUtil
import com.tencent.bkrepo.docker.util.UserUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

/**
 *
 *  ManifestImpl validates and impl the manifest interface
 * @author: owenlxu
 * @date: 2019-10-03
 */

@RestController
class ManifestImpl @Autowired constructor(val dockerRepo: DockerV2LocalRepoService) : Manifest {

    override fun putManifest(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String,
        tag: String,
        contentType: String,
        artifactFile: ArtifactFile
    ): ResponseEntity<Any> {
        val uId = UserUtil.getContextUserId(userId)
        val name = PathUtil.artifactName(request, MANIFEST_PATTERN, projectId, repoName)
        val pathContext = RequestContext(uId, projectId, repoName, name)
        return dockerRepo.uploadManifest(pathContext, tag, contentType, artifactFile)
    }

    override fun getManifest(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String,
        reference: String
    ): ResponseEntity<Any> {
        val name = PathUtil.artifactName(request, MANIFEST_PATTERN, projectId, repoName)
        val uId = UserUtil.getContextUserId(userId)
        val pathContext = RequestContext(uId, projectId, repoName, name)
        return dockerRepo.getManifest(pathContext, reference)
    }

    override fun existManifest(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String,
        reference: String
    ): ResponseEntity<Any> {
        val name = PathUtil.artifactName(request, MANIFEST_PATTERN, projectId, repoName)
        val uId = UserUtil.getContextUserId(userId)
        val pathContext = RequestContext(uId, projectId, repoName, name)
        return dockerRepo.getManifest(pathContext, reference)
    }
}
