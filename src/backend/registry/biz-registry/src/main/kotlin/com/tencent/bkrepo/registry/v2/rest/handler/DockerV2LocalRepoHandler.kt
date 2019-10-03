package com.tencent.bkrepo.registry.v2.rest.handler

import com.tencent.bkrepo.registry.DockerWorkContext
import com.tencent.bkrepo.registry.repomd.Artifact
import com.tencent.bkrepo.registry.repomd.Repo
import com.tencent.bkrepo.registry.repomd.util.PathUtils
import com.tencent.bkrepo.registry.v2.model.DockerDigest
import com.tencent.bkrepo.registry.v2.rest.errors.DockerV2Errors
import java.util.function.Predicate
import java.util.regex.Pattern
import javax.ws.rs.core.Response
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory

class DockerV2LocalRepoHandler : DockerV2RepoHandler {
    var log = LoggerFactory.getLogger(DockerV2LocalRepoHandler::class.java)
    var OLD_USER_AGENT_PATTERN = Pattern.compile("^(?:docker\\/1\\.(?:3|4|5|6|7(?!\\.[0-9]-dev))|Go ).*$")
    var ERR_MANIFEST_UPLOAD = "Error uploading manifest: "
    lateinit var repo: Repo<DockerWorkContext>
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

    override fun deleteManifest(dockerRepo: String, reference: String): Response {
        try {
            val digest = DockerDigest(reference)
            return this.deleteManifestByDigest(dockerRepo, digest)
        } catch (var4: Exception) {
            log.trace("Unable to parse digest, deleting manifest by tag '{}'", reference)
            return this.deleteManifestByTag(dockerRepo, reference)
        }
    }

    private fun deleteManifestByDigest(dockerRepo: String, digest: DockerDigest): Response {
        log.info("Deleting docker manifest for repo '{}' and digest '{}' in repo '{}'", *arrayOf(dockerRepo, digest, this.repo.getRepoId()))
        val manifests = this.repo.findArtifacts(dockerRepo, "manifest.json")
        val var4 = manifests.iterator()

        while (var4.hasNext()) {
            val manifest = var4.next() as Artifact
            if (this.repo.canWrite(manifest.getPath())) {
                val manifestDigest = this.repo.getAttribute(manifest.getPath(), digest.getDigestAlg()) as String
                if (StringUtils.isNotBlank(manifestDigest) && StringUtils.equals(manifestDigest, digest.getDigestHex()) && this.repo.delete(PathUtils.getParent(manifest.getPath())!!)) {
                    return Response.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").build()
                }
            }
        }

        return DockerV2Errors.manifestUnknown(digest.toString())
    }

    private fun deleteManifestByTag(dockerRepo: String, tag: String): Response {
        val tagPath = "$dockerRepo/$tag"
        val manifestPath = "$tagPath/manifest.json"
        if (!this.repo.exists(manifestPath)) {
            return DockerV2Errors.manifestUnknown(manifestPath)
        } else if (this.repo.delete(tagPath)) {
            return Response.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").build()
        } else {
            log.warn("Unable to delete tag '{}'", manifestPath)
            return DockerV2Errors.manifestUnknown(manifestPath)
        }
    }
}
