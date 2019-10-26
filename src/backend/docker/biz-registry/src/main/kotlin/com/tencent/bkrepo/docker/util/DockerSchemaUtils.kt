package com.tencent.bkrepo.docker.util

import com.fasterxml.jackson.databind.JsonNode
import com.tencent.bkrepo.docker.DockerWorkContext
import com.tencent.bkrepo.docker.artifact.repomd.DockerArtifactoryService
import com.tencent.bkrepo.docker.artifact.repomd.DockerPackageWorkContext
import com.tencent.bkrepo.docker.manifest.ManifestType
import com.tencent.bkrepo.docker.repomd.Artifact
import com.tencent.bkrepo.docker.repomd.Repo
import com.tencent.bkrepo.docker.v2.helpers.DockerSearchBlobPolicy
import com.tencent.bkrepo.docker.v2.model.DockerDigest
import java.io.IOException
import javax.xml.bind.DatatypeConverter
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity

class DockerSchemaUtils {
    companion object {
        private val log = LoggerFactory.getLogger(DockerSchemaUtils::class.java)
        val EMPTY_BLOB_CONTENT = DatatypeConverter.parseHexBinary("1f8b080000096e8800ff621805a360148c5800080000ffff2eafb5ef00040000")
        private val EMPTY_BLOB_SIZE = 32

        fun getManifestType(manifestPath: String, repo: Repo<DockerWorkContext>): ManifestType {
            val manifestType = repo.getAttribute(manifestPath, "docker.manifest.type") as String
            return if (StringUtils.isBlank(manifestType)) ManifestType.Schema1Signed else ManifestType.from(manifestType)
        }

        fun isEmptyBlob(digest: DockerDigest): Boolean {
            return digest.toString().equals(emptyBlobDigest().toString())
        }

        fun emptyBlobDigest(): DockerDigest {
            return DockerDigest("sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4")
        }

        fun emptyBlobHeadResponse(): ResponseEntity<Any> {
            return ResponseEntity.ok().header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Content-Digest", emptyBlobDigest().toString()).header("Content-Length", "32").header("Content-Type", "application/octet-stream").build()
        }

        fun emptyBlobGetResponse(): ResponseEntity<Any> {
            return ResponseEntity.ok().header("Content-Length", "32").header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Content-Digest", emptyBlobDigest().toString()).header("Content-Type", "application/octet-stream").body(EMPTY_BLOB_CONTENT)
        }

        fun fetchSchema2ManifestConfig(repo: DockerArtifactoryService, manifestBytes: ByteArray, dockerRepoPath: String, tag: String): ByteArray {
            try {
                val manifest = JsonUtil.readTree(manifestBytes)
                if (manifest != null) {
                    val digest = manifest.get("config").get("digest").asText()
                    val manifestConfigFilename = DockerDigest(digest).filename()
                    val manifestConfigFile = DockerUtils.getManifestConfigBlob(repo, manifestConfigFilename, dockerRepoPath, tag)
                    if (manifestConfigFile != null) {
                        val manifestStream = (repo.getWorkContextC() as DockerWorkContext).readGlobal(DockerUtils.getFullPath(manifestConfigFile, repo.getWorkContextC() as DockerWorkContext))
                        var var9: Throwable? = null

                        val var10: ByteArray
                        try {
                            var10 = IOUtils.toByteArray(manifestStream!!)
                        } catch (var20: Throwable) {
                            var9 = var20
                            throw var20
                        } finally {
                            if (manifestStream != null) {
                                if (var9 != null) {
                                    try {
                                        manifestStream!!.close()
                                    } catch (var19: Throwable) {
                                        var9.addSuppressed(var19)
                                    }
                                } else {
                                    manifestStream!!.close()
                                }
                            }
                        }

                        return var10
                    }
                }
            } catch (var22: IOException) {
                log.error("Error fetching manifest schema2: " + var22.message, var22)
            }

            return ByteArray(0)
        }

        fun fetchSchema2Manifest(repo: DockerArtifactoryService, schema2Path: String): ByteArray {
            try {
                val manifestStream = (repo.getWorkContextC() as DockerWorkContext).readGlobal(schema2Path)
                var var3: Throwable? = null

                val var4: ByteArray
                try {
                    var4 = IOUtils.toByteArray(manifestStream!!)
                } catch (var14: Throwable) {
                    var3 = var14
                    throw var14
                } finally {
                    if (manifestStream != null) {
                        if (var3 != null) {
                            try {
                                manifestStream!!.close()
                            } catch (var13: Throwable) {
                                var3.addSuppressed(var13)
                            }
                        } else {
                            manifestStream!!.close()
                        }
                    }
                }

                return var4
            } catch (var16: IOException) {
                log.error("Error fetching manifest schema2: " + var16.message, var16)
                return ByteArray(0)
            }
        }

        fun fetchSchema2Path(repo: DockerArtifactoryService, dockerRepo: String, manifestListBytes: ByteArray, searchGlobally: Boolean): String {
            try {
                val manifestList = JsonUtil.readTree(manifestListBytes)
                if (manifestList != null) {
                    val manifests = manifestList.get("manifests")
                    val var6 = manifests.iterator()

                    while (var6.hasNext()) {
                        val manifest = var6.next() as JsonNode
                        val platform = manifest.get("platform")
                        val architecture = platform.get("architecture").asText()
                        val os = platform.get("os").asText()
                        if (StringUtils.equals(architecture, "amd64") && StringUtils.equals(os, "linux")) {
                            val digest = manifest.get("digest").asText()
                            val manifestFilename = DockerDigest(digest).filename()
                            if (searchGlobally) {
                                val manifestFile = DockerUtils.getBlobGlobally(repo, manifestFilename, DockerSearchBlobPolicy.SHA_256)
                                return if (manifestFile == null) "" else DockerUtils.getFullPath(manifestFile, repo.getWorkContextC() as DockerWorkContext)
                            }

                            val artifacts = repo.findArtifacts(dockerRepo, manifestFilename)
                            if (artifacts != null && artifacts!!.iterator().hasNext()) {
                                return (artifacts!!.iterator().next() as Artifact).getArtifactPath()
                            }
                        }
                    }
                }
            } catch (var14: IOException) {
                log.error("Error fetching manifest list: " + var14.message, var14)
            }

            return ""
        }
    }
}
