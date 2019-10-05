package com.tencent.bkrepo.registry.v2.rest.handler

import com.fasterxml.jackson.databind.JsonNode
import com.google.common.base.Joiner
import com.tencent.bkrepo.registry.DockerWorkContext
import com.tencent.bkrepo.registry.exception.DockerLockManifestException
import com.tencent.bkrepo.registry.exception.DockerNotFoundException
import com.tencent.bkrepo.registry.exception.DockerSyncManifestException
import com.tencent.bkrepo.registry.manifest.ManifestDeserializer
import com.tencent.bkrepo.registry.manifest.ManifestJson
import com.tencent.bkrepo.registry.manifest.ManifestListSchema2Deserializer
import com.tencent.bkrepo.registry.manifest.ManifestType
import com.tencent.bkrepo.registry.repomd.Artifact
import com.tencent.bkrepo.registry.repomd.Repo
import com.tencent.bkrepo.registry.repomd.UploadContext
import com.tencent.bkrepo.registry.repomd.util.PathUtils
import com.tencent.bkrepo.registry.util.DockerSchemaUtils
import com.tencent.bkrepo.registry.util.DockerUtils
import com.tencent.bkrepo.registry.util.JsonUtil
import com.tencent.bkrepo.registry.v2.helpers.DockerManifestDigester
import com.tencent.bkrepo.registry.v2.helpers.DockerManifestSyncer
import com.tencent.bkrepo.registry.v2.helpers.DockerSearchBlobPolicy
import com.tencent.bkrepo.registry.v2.model.DockerBlobInfo
import com.tencent.bkrepo.registry.v2.model.DockerDigest
import com.tencent.bkrepo.registry.v2.model.ManifestMetadata
import com.tencent.bkrepo.registry.v2.rest.errors.DockerV2Errors
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
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
    lateinit var manifestSyncer: DockerManifestSyncer
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

    override fun uploadManifest(dockerRepo: String, tag: String, mediaType: String, data: ByteArray): Response {
        log.info("Deploying docker manifest for repo '{}' and tag '{}' into repo '{}'", *arrayOf<Any>(dockerRepo, tag, this.repo.getRepoId()))
        var lockId: String = ""
        val manifestType = ManifestType.from(mediaType)
        val manifestPath = buildManifestPathFromType(dockerRepo, tag, manifestType)
        if (!this.repo.canWrite(manifestPath)) {
            log.debug("Attempt to write manifest to {} failed the permission check.", manifestPath)
            return DockerV2Errors.unauthorizedUpload()
            // return this.consumeStreamAndReturnError(dockerRepo, tag, stream)
        } else {
            val var9: Response
            try {
                lockId = (this.repo.getWorkContextC() as DockerWorkContext).obtainManifestLock("$dockerRepo/$tag")
                val digest = this.processUploadedManifestType(dockerRepo, tag, manifestPath, manifestType, data)
                return Response.status(201).header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Content-Digest", digest).build()
            } catch (var14: DockerLockManifestException) {
                // this.logException(var14)
                var9 = DockerV2Errors.manifestConcurrent(var14.message!!)
                return var9
            } catch (var15: Exception) {
                // this.logException(var15)
                var9 = DockerV2Errors.manifestInvalid(var15.message!!)
            } finally {
                // IOUtils.closeQuietly(stream)
                this.releaseManifestLock(lockId, dockerRepo, tag)
                (this.repo.getWorkContextC() as DockerWorkContext).cleanup(this.repo.getRepoId(), "$dockerRepo/_uploads")
            }

            return var9
        }
    }

    private fun buildManifestPathFromType(dockerRepo: String, tag: String, manifestType: ManifestType): String {
        val manifestPath: String
        if (ManifestType.Schema2List.equals(manifestType)) {
            manifestPath = Joiner.on("/").join(dockerRepo, tag, *arrayOf<Any>("list.manifest.json"))
        } else {
            manifestPath = Joiner.on("/").join(dockerRepo, tag, *arrayOf<Any>("manifest.json"))
        }

        return manifestPath
    }

    @Throws(IOException::class, DockerSyncManifestException::class)
    private fun processUploadedManifestType(dockerRepo: String, tag: String, manifestPath: String, manifestType: ManifestType, manifestBytes: ByteArray): DockerDigest {
        // val manifestBytes = IOUtils.toByteArray(stream)
        val digest = DockerManifestDigester.calc(manifestBytes)
        if (ManifestType.Schema2List.equals(manifestType)) {
            this.processManifestList(dockerRepo, tag, manifestPath, digest!!, manifestBytes, manifestType)
            return digest
        } else {
            val manifestMetadata = ManifestDeserializer.deserialize(this.repo, dockerRepo, tag, manifestType, manifestBytes, digest!!)
            this.addManifestsBlobs(manifestType, manifestBytes, manifestMetadata)
            if (!this.manifestSyncer.sync(this.repo, manifestMetadata, dockerRepo, tag)) {
                val msg = "Failed syncing manifest blobs, canceling manifest upload"
                log.error(msg)
                throw DockerSyncManifestException(msg)
            } else {
                val response = this.repo.upload(this.manifestUploadContext(manifestType, manifestMetadata, manifestPath, manifestBytes))
                if (!this.uploadSuccessful(response)) {
                    throw IOException(response.getEntity().toString())
                } else {
                    val params = this.buildManifestPropertyMap(dockerRepo, tag, digest!!, manifestType)
                    this.repo.setAttributes(manifestPath, params)
                    val labels = manifestMetadata.tagInfo.labels
                    val var12 = labels.keySet().iterator()

                    while (var12.hasNext()) {
                        val labelKey = var12.next() as String
                        this.repo.setAttributes(manifestPath, "docker.label.$labelKey", labels.get(labelKey).toTypedArray())
                    }

                    (this.repo.getWorkContextC() as DockerWorkContext).onTagPushedSuccessfully(this.repo.getRepoId(), dockerRepo, tag)
                    return digest
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun processManifestList(dockerRepo: String, tag: String, manifestPath: String, digest: DockerDigest, manifestBytes: ByteArray, manifestType: ManifestType) {
        val manifestList = ManifestListSchema2Deserializer.deserialize(manifestBytes)
        if (manifestList != null) {
            val var8 = manifestList!!.manifests.iterator()

            while (var8.hasNext()) {
                val manifest = var8.next() as ManifestJson
                val mDigest = manifest.digest
                val manifestFilename = DockerDigest(mDigest!!).filename()
                val manifestFile = DockerUtils.getBlobGlobally(this.repo, manifestFilename, DockerSearchBlobPolicy.SHA_256)
                        ?: throw DockerNotFoundException("Manifest list (" + digest.toString() + ") Missing manifest digest " + mDigest + ". ==>" + manifest.toString())
            }
        }

        val response = this.repo.upload(this.manifestListUploadContext(manifestType, digest, manifestPath, manifestBytes))
        if (this.uploadSuccessful(response)) {
            val params = this.buildManifestPropertyMap(dockerRepo, tag, digest, manifestType)
            this.repo.setAttributes(manifestPath, params)
            (this.repo.getWorkContextC() as DockerWorkContext).onTagPushedSuccessfully(this.repo.getRepoId(), dockerRepo, tag)
        } else {
            throw IOException(response.getEntity().toString())
        }
    }

    private fun manifestListUploadContext(manifestType: ManifestType, digest: DockerDigest, manifestPath: String, manifestBytes: ByteArray): UploadContext {
        val context = UploadContext(manifestPath).content(ByteArrayInputStream(manifestBytes))
        if (manifestType.equals(ManifestType.Schema2List) && "sha256" == digest.getDigestAlg()) {
            context.sha256(digest.getDigestHex())
        }

        return context
    }

    private fun uploadSuccessful(response: Response): Boolean {
        val status = response.status
        return status == Response.Status.OK.statusCode || status == Response.Status.CREATED.statusCode
    }

    private fun buildManifestPropertyMap(dockerRepo: String, tag: String, digest: DockerDigest, manifestType: ManifestType): Map<String, String> {
        return emptyMap()
        // TODO:
        // return Stream.of(Pair(digest.getDigestAlg(), digest.getDigestHex()), Pair("docker.manifest.digest", digest.toString()), Pair<String, String>("docker.manifest", tag), Pair<String, String>("docker.repoName", dockerRepo), Pair("docker.manifest.type", manifestType.toString())).collect(Collectors.toMap<Any, Any, Any>(Function<Any, Any> { it.key }, Function<Any, Any> { it.value }))
    }

    private fun releaseManifestLock(lockId: String, dockerRepo: String, tag: String) {
        if (lockId != null) {
            try {
                (this.repo.getWorkContextC() as DockerWorkContext).releaseManifestLock(lockId, "$dockerRepo/$tag")
            } catch (var5: Exception) {
                log.error("Error uploading manifest: '{}'", var5.message)
                log.debug("Error uploading manifest: " + var5.message, var5)
            }
        }
    }

    @Throws(IOException::class)
    private fun addManifestsBlobs(manifestType: ManifestType, manifestBytes: ByteArray, manifestMetadata: ManifestMetadata) {
        if (ManifestType.Schema2.equals(manifestType)) {
            this.addSchema2Blob(manifestBytes, manifestMetadata)
        } else if (ManifestType.Schema2List.equals(manifestType)) {
            this.addSchema2ListBlobs(manifestBytes, manifestMetadata)
        }
    }

    @Throws(IOException::class)
    private fun addSchema2Blob(manifestBytes: ByteArray, manifestMetadata: ManifestMetadata) {
        val manifest = JsonUtil.readTree(manifestBytes)
        if (manifest != null) {
            val config = manifest!!.get("config")
            if (config != null) {
                val digest = config!!.get("digest").asText()
                val blobInfo = DockerBlobInfo("", digest, 0L, "")
                manifestMetadata.blobsInfo.add(blobInfo)
            }
        }
    }

    @Throws(IOException::class)
    private fun addSchema2ListBlobs(manifestBytes: ByteArray, manifestMetadata: ManifestMetadata) {
        val manifestList = JsonUtil.readTree(manifestBytes)
        if (manifestList != null) {
            val manifests = manifestList.get("manifests")
            val var5 = manifests.iterator()

            while (var5.hasNext()) {
                val manifest = var5.next() as JsonNode
                val digest = manifest.get("platform").get("digest").asText()
                val dockerBlobInfo = DockerBlobInfo("", digest, 0L, "")
                manifestMetadata.blobsInfo.add(dockerBlobInfo)
                val manifestFilename = DockerDigest(digest).filename()
                val manifestFile = DockerUtils.getBlobGlobally(this.repo, manifestFilename, DockerSearchBlobPolicy.SHA_256)
                if (manifestFile != null) {
                    val configBytes = DockerSchemaUtils.fetchSchema2Manifest(this.repo, DockerUtils.getFullPath(manifestFile, this.repo.getWorkContextC() as DockerWorkContext))
                    this.addSchema2Blob(configBytes, manifestMetadata)
                }
            }
        }
    }

    private fun manifestUploadContext(manifestType: ManifestType, manifestMetadata: ManifestMetadata, manifestPath: String, manifestBytes: ByteArray): UploadContext {
        val context = UploadContext(manifestPath).content(ByteArrayInputStream(manifestBytes))
        if ((manifestType.equals(ManifestType.Schema2) || manifestType.equals(ManifestType.Schema2List)) && manifestMetadata.tagInfo != null && manifestMetadata.tagInfo.digest != null && "sha256" == manifestMetadata.tagInfo.digest.getDigestAlg()) {
            context.sha256(manifestMetadata.tagInfo.digest.getDigestHex())
        }

        return context
    }

    private fun consumeStreamAndReturnError(dockerRepo: String, identifier: String, stream: InputStream): Response {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
//        try {
//            val output = NullOutputStream()
//            var var5: Throwable? = null
//
//            try {
//                IOUtils.copy(stream, output)
//            } catch (var23: Throwable) {
//                var5 = var23
//                throw var23
//            } finally {
//                if (output != null !!) {
//                    if (var5 != null) {
//                        try {
//                            output.close()
//                        } catch (var22: Throwable) {
//                            var5.addSuppressed(var22)
//                        }
//                    } else {
//                        output.close()
//                    }
//                }
//            }
//        } catch (var25: IOException) {
//            log.debug("Failed to consume incoming stream for " + this.repo.getRepoId() + ":" + dockerRepo + "/" + identifier, var25)
//        } finally {
//            IOUtils.closeQuietly(stream)
//        }
//
//        return DockerV2Errors.unauthorizedUpload()
    }
}
