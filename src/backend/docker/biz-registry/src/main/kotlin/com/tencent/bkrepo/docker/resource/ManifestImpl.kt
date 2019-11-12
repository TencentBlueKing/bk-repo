package com.tencent.bkrepo.docker.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bkrepo.docker.api.Manifest
import com.tencent.bkrepo.docker.v2.model.DockerDigest
import com.tencent.bkrepo.docker.v2.rest.handler.DockerV2LocalRepoHandler
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.core.Response
import okhttp3.MediaType
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
class ManifestImpl @Autowired constructor(val dockerRepo: DockerV2LocalRepoHandler) : Manifest {

    override fun putManifest(
        projectId: String,
        repoName: String,
        name: String,
        tag: String,
        contentType: String,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        return dockerRepo.uploadManifest(projectId, repoName, name, tag,contentType, request.inputStream)
    }
}
