package com.tencent.bkrepo.registry.v2.rest.handler

import com.tencent.bkrepo.registry.repomd.Artifact
import java.util.function.Predicate
import java.util.regex.Pattern
import javax.ws.rs.core.Response
import org.slf4j.LoggerFactory

class DockerV2LocalRepoHandler : DockerV2RepoHandler {
    var log = LoggerFactory.getLogger(DockerV2LocalRepoHandler::class.java)
    var OLD_USER_AGENT_PATTERN = Pattern.compile("^(?:docker\\/1\\.(?:3|4|5|6|7(?!\\.[0-9]-dev))|Go ).*$")
    var ERR_MANIFEST_UPLOAD = "Error uploading manifest: "
//    var  repo: Repo<DockerWorkContext>
//    var  httpHeaders: HttpHeaders
    // private val manifestSyncer: DockerManifestSyncer
    var nonTempUploads: Predicate<Artifact> = object : Predicate<Artifact> {
        private val TMP_UPLOADS_PATH_ELEMENT = "/_uploads/"

        override fun test(artifact: Artifact): Boolean {
            return !artifact.getPath().contains("/_uploads/")
        }
    }

    override fun ping(): Response {
        return Response.ok("{}").header("Docker-Distribution-Api-Version", "registry/2.0").build()
    }
}
