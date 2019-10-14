package com.tencent.bkrepo.registry.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bkrepo.registry.api.Manifest
import com.tencent.bkrepo.registry.artifact.util.DockerUtil
import com.tencent.bkrepo.registry.v2.rest.handler.DockerV2LocalRepoHandler
import javax.ws.rs.core.Response
import okhttp3.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

// import com.tencent.bkrepo.common.api.pojo.Response

/**
 * 元数据服务接口实现类
 *
 * @author: owenlxu
 * @date: 2019-10-03
 */

// ManifestImpl validates and impl the manifest interface
@RestController
class ManifestImpl @Autowired constructor(val objectMapper: ObjectMapper) : Manifest {

    override fun putManifest(
        repoKey: String,
        name: String,
        reference: String,
        contentTypeHeader: String,
        body: String
    ): Response {
        var mediaType = MediaType.parse(contentTypeHeader).toString()
//        var headers: HttpHeaders
//        print(mediaType)
        var dockerRepoContext = DockerUtil.createDockerRepoContext(repoKey)
        var repoHandler = DockerV2LocalRepoHandler(dockerRepoContext)
        var response = repoHandler.uploadManifest(name, reference, mediaType, body.toByteArray())
        // return response.toString()
        return Response.ok(mediaType).build()
    }
}
