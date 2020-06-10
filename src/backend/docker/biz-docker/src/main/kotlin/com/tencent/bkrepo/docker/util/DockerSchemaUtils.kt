package com.tencent.bkrepo.docker.util

import com.fasterxml.jackson.databind.JsonNode
import com.tencent.bkrepo.docker.artifact.DockerArtifactService
import com.tencent.bkrepo.docker.context.DownloadContext
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.model.DockerDigest
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import java.io.IOException
import javax.xml.bind.DatatypeConverter

class DockerSchemaUtils {

    companion object {
        private val logger = LoggerFactory.getLogger(DockerSchemaUtils::class.java)
        val EMPTY_BLOB_CONTENT =
            DatatypeConverter.parseHexBinary("1f8b080000096e8800ff621805a360148c5800080000ffff2eafb5ef00040000")
        private val EMPTY_BLOB_SIZE = 32

        fun getManifestType(
            projectId: String,
            repoName: String,
            manifestPath: String,
            repo: DockerArtifactService
        ): String? {
            return repo.getAttribute(projectId, repoName, manifestPath, "docker.manifest.type")
        }

        fun isEmptyBlob(digest: DockerDigest): Boolean {
            return digest.toString().equals(emptyBlobDigest().toString())
        }

        fun emptyBlobDigest(): DockerDigest {
            return DockerDigest("sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4")
        }

        fun emptyBlobHeadResponse(): ResponseEntity<Any> {
            return ResponseEntity.ok().header("Docker-Distribution-Api-Version", "registry/2.0")
                .header("Docker-Content-Digest", emptyBlobDigest().toString()).header("Content-Length", "32")
                .header("Content-Type", "application/octet-stream").build()
        }

        fun emptyBlobGetResponse(): ResponseEntity<Any> {
            return ResponseEntity.ok().header("Content-Length", "32")
                .header("Docker-Distribution-Api-Version", "registry/2.0")
                .header("Docker-Content-Digest", emptyBlobDigest().toString())
                .header("Content-Type", "application/octet-stream").body(EMPTY_BLOB_CONTENT)
        }

        fun fetchSchema2ManifestConfig(
            repo: DockerArtifactService,
            pathContext: RequestContext,
            manifestBytes: ByteArray,
            tag: String
        ): ByteArray {
            try {
                val manifest = JsonUtil.readTree(manifestBytes)
                val digest = manifest.get("config").get("digest").asText()
                val manifestConfigFilename = DockerDigest(digest).filename()
                val manifestConfigFile = DockerUtils.getManifestConfigBlob(
                    repo,
                    manifestConfigFilename,
                    pathContext,
                    tag
                )
                logger.info("fetch manifest config file {}", manifestConfigFile!!.sha256)
                val manifestStream = repo.readGlobal(
                    DownloadContext(pathContext.projectId, pathContext.repoName, manifestConfigFile.path)
                        .sha256(manifestConfigFile.sha256!!).length(manifestConfigFile.contentLength)
                )
                manifestStream.use {
                    var bytes = IOUtils.toByteArray(it)
                    logger.info("config blob data size [bytes.size]")
                    return bytes
                }
            } catch (exception: IOException) {
                logger.error("error fetching manifest schema2: [$exception] ")
            }

            return ByteArray(0)
        }

        fun fetchSchema2Manifest(repo: DockerArtifactService, schema2Path: String): ByteArray {
            val manifestStream = repo.getWorkContextC().readGlobal(schema2Path)
            manifestStream.use {
                return IOUtils.toByteArray(it)
            }
        }

        fun fetchSchema2Path(
            repo: DockerArtifactService,
            pathContext: RequestContext,
            manifestListBytes: ByteArray,
            searchGlobally: Boolean
        ): String {
            try {
                val manifestList = JsonUtil.readTree(manifestListBytes)
                val manifests = manifestList.get("manifests")
                val maniIter = manifests.iterator()

                while (maniIter.hasNext()) {
                    val manifest = maniIter.next() as JsonNode
                    val platform = manifest.get("platform")
                    val architecture = platform.get("architecture").asText()
                    val os = platform.get("os").asText()
                    if (StringUtils.equals(architecture, "amd64") && StringUtils.equals(os, "linux")) {
                        val digest = manifest.get("digest").asText()
                        val manifestFilename = DockerDigest(digest).filename()
                        if (searchGlobally) {
                            val manifestFile = DockerUtils.findBlobGlobally(
                                repo,
                                pathContext,
                                manifestFilename
                            )
                            return if (manifestFile == null) "" else DockerUtils.getFullPath(
                                manifestFile,
                                repo.getWorkContextC()
                            )
                        }

                        val artifact =
                            repo.artifact(pathContext.projectId, pathContext.repoName, pathContext.dockerRepo)
                        return artifact!!.path
                    }
                }
            } catch (ioException: IOException) {
                logger.error("Error fetching manifest list: " + ioException.message, ioException)
            }

            return ""
        }
    }
}
