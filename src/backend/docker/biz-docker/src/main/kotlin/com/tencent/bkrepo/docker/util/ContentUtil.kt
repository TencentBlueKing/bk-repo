package com.tencent.bkrepo.docker.util

import com.fasterxml.jackson.databind.JsonNode
import com.tencent.bkrepo.docker.artifact.DockerArtifactRepo
import com.tencent.bkrepo.docker.constant.EMPTYSTR
import com.tencent.bkrepo.docker.context.DownloadContext
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.model.DockerDigest
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import javax.xml.bind.DatatypeConverter

class ContentUtil {

    companion object {
        private val logger = LoggerFactory.getLogger(ContentUtil::class.java)
        val EMPTY_BLOB_CONTENT =
            DatatypeConverter.parseHexBinary("1f8b080000096e8800ff621805a360148c5800080000ffff2eafb5ef00040000")

        fun isEmptyBlob(digest: DockerDigest): Boolean {
            return digest.toString() == emptyBlobDigest().toString()
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

        // get manifest type by path
        fun getManifestType(
            repo: DockerArtifactRepo,
            projectId: String,
            repoName: String,
            manifestPath: String
        ): String? {
            return repo.getAttribute(projectId, repoName, manifestPath, "docker.manifest.type")
        }

        // get manifest config file byte array from manifest
        fun getSchema2ManifestConfigContent(
            repo: DockerArtifactRepo,
            context: RequestContext,
            bytes: ByteArray,
            tag: String
        ): ByteArray {
            val manifest = JsonUtil.readTree(bytes)
                val digest = manifest.get("config").get("digest").asText()
            val fileName = DockerDigest(digest).fileName()
            val configFile = ArtifactUtil.getManifestConfigBlob(repo, fileName, context, tag) ?: run {
                return ByteArray(0)
            }
            logger.info("fetch manifest config file [$configFile]")
            val downloadContext = DownloadContext(context).sha256(configFile.sha256!!).length(configFile.length)
            val stream = repo.download(downloadContext)
            stream.use {
                return IOUtils.toByteArray(it)
            }
        }

        fun getSchema2ManifestContent(repo: DockerArtifactRepo, context: RequestContext, schema2Path: String): ByteArray {
            val manifest = ArtifactUtil.getManifestByName(repo, context, schema2Path) ?: run {
                return ByteArray(0)
            }
            val downloadContext = DownloadContext(context).sha256(manifest.sha256!!).length(manifest.length)
            val stream = repo.download(downloadContext)
            stream.use {
                return IOUtils.toByteArray(it)
            }
        }

        fun getSchema2Path(repo: DockerArtifactRepo, context: RequestContext, bytes: ByteArray): String {
            val manifestList = JsonUtil.readTree(bytes)
            val manifests = manifestList.get("manifests")
            val maniIter = manifests.iterator()
            while (maniIter.hasNext()) {
                val manifest = maniIter.next() as JsonNode
                val platform = manifest.get("platform")
                val architecture = platform.get("architecture").asText()
                val os = platform.get("os").asText()
                if (StringUtils.equals(architecture, "amd64") && StringUtils.equals(os, "linux")) {
                    val digest = manifest.get("digest").asText()
                    val fileName = DockerDigest(digest).fileName()
                    val manifestFile = ArtifactUtil.getBlobByName(repo, context, fileName) ?: run {
                        return EMPTYSTR
                    }
                    return ArtifactUtil.getFullPath(manifestFile)
                }
            }
            return EMPTYSTR
        }
    }
}
