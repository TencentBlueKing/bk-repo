package com.tencent.bkrepo.docker.v2.rest.handler

import com.fasterxml.jackson.databind.JsonNode
import com.google.common.base.Joiner
import com.tencent.bkrepo.docker.DockerWorkContext
import com.tencent.bkrepo.docker.artifact.repomd.DockerArtifactoryService
import com.tencent.bkrepo.docker.artifact.util.DockerUtil
import com.tencent.bkrepo.docker.exception.DockerNotFoundException
import com.tencent.bkrepo.docker.exception.DockerSyncManifestException
import com.tencent.bkrepo.docker.manifest.ManifestDeserializer
import com.tencent.bkrepo.docker.manifest.ManifestJson
import com.tencent.bkrepo.docker.manifest.ManifestListSchema2Deserializer
import com.tencent.bkrepo.docker.manifest.ManifestType
import com.tencent.bkrepo.docker.repomd.Artifact
import com.tencent.bkrepo.docker.repomd.Repo
import com.tencent.bkrepo.docker.repomd.UploadContext
import com.tencent.bkrepo.docker.util.DockerSchemaUtils
import com.tencent.bkrepo.docker.util.DockerUtils
import com.tencent.bkrepo.docker.util.JsonUtil
import com.tencent.bkrepo.docker.v2.helpers.DockerManifestDigester
import com.tencent.bkrepo.docker.v2.helpers.DockerManifestSyncer
import com.tencent.bkrepo.docker.v2.helpers.DockerSearchBlobPolicy
import com.tencent.bkrepo.docker.v2.model.DockerBlobInfo
import com.tencent.bkrepo.docker.v2.model.DockerDigest
import com.tencent.bkrepo.docker.v2.model.ManifestMetadata
import com.tencent.bkrepo.docker.v2.rest.errors.DockerV2Errors
import org.apache.commons.io.FileUtils
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.*
import java.util.function.Predicate
import java.util.regex.Pattern
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriBuilder
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.NullOutputStream
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import com.tencent.bkrepo.common.storage.util.DataDigestUtils
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import javax.servlet.http.HttpServletRequest

@Service
class DockerV2LocalRepoHandler @Autowired constructor(
        private val repo : DockerArtifactoryService
) : DockerV2RepoHandler {

    public var httpHeaders: HttpHeaders  = HttpHeaders.EMPTY

    companion object {
        private val manifestSyncer = DockerManifestSyncer()
        private val log = LoggerFactory.getLogger(DockerV2LocalRepoHandler::class.java)
        private val ERR_MANIFEST_UPLOAD = "Error uploading manifest: "
        private val OLD_USER_AGENT_PATTERN = Pattern.compile("^(?:docker\\/1\\.(?:3|4|5|6|7(?!\\.[0-9]-dev))|Go ).*$")
    }

    var nonTempUploads: Predicate<Artifact> = object : Predicate<Artifact> {
        private val TMP_UPLOADS_PATH_ELEMENT = "/_uploads/"

        override fun test(artifact: Artifact): Boolean {
            return !artifact.getArtifactPath().contains("/_uploads/")
        }
    }

    override fun ping(): Response {
        return Response.ok("{}").header("Docker-Distribution-Api-Version", "registry/2.0").build()
    }

//    override fun deleteManifest(dockerRepo: String, reference: String): Response {
//        try {
//            val digest = DockerDigest(reference)
//            return this.deleteManifestByDigest(dockerRepo, digest)
//        } catch (var4: Exception) {
//            log.trace("Unable to parse digest, deleting manifest by tag '{}'", reference)
//            return this.deleteManifestByTag(dockerRepo, reference)
//        }
//    }
//
//    private fun deleteManifestByDigest(dockerRepo: String, digest: DockerDigest): Response {
//        log.info("Deleting docker manifest for repo '{}' and digest '{}' in repo '{}'", *arrayOf(dockerRepo, digest, this.repo.getRepoId()))
//        val manifests = this.repo.findArtifacts(dockerRepo, "manifest.json")
//        val var4 = manifests.iterator()
//
//        while (var4.hasNext()) {
//            val manifest = var4.next() as Artifact
//            if (this.repo.canWrite(manifest.getPath())) {
//                val manifestDigest = this.repo.getAttribute(manifest.getPath(), digest.getDigestAlg()) as String
//                if (StringUtils.isNotBlank(manifestDigest) && StringUtils.equals(manifestDigest, digest.getDigestHex()) && this.repo.delete(PathUtils.getParent(manifest.getPath())!!)) {
//                    return Response.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").build()
//                }
//            }
//        }
//
//        return DockerV2Errors.manifestUnknown(digest.toString())
//    }

//    private fun deleteManifestByTag(dockerRepo: String, tag: String): Response {
//        val tagPath = "$dockerRepo/$tag"
//        val manifestPath = "$tagPath/manifest.json"
//        if (!this.repo.exists(manifestPath)) {
//            return DockerV2Errors.manifestUnknown(manifestPath)
//        } else if (this.repo.delete(tagPath)) {
//            return Response.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").build()
//        } else {
//            log.warn("Unable to delete tag '{}'", manifestPath)
//            return DockerV2Errors.manifestUnknown(manifestPath)
//        }
//    }

//    override fun uploadManifest(dockerRepo: String, tag: String, mediaType: String, data: ByteArray): Response {
//        log.info("Deploying docker manifest for repo '{}' and tag '{}' into repo '{}'", *arrayOf<Any>(dockerRepo, tag, this.repo.getRepoId()))
//        var lockId: String = ""
//        val manifestType = ManifestType.from(mediaType)
//        val manifestPath = buildManifestPathFromType(dockerRepo, tag, manifestType)
//        log.info("manifest path to {} .", manifestPath)
//        if (!this.repo.canWrite(manifestPath)) {
//            log.debug("Attempt to write manifest to {} failed the permission check.", manifestPath)
//            return DockerV2Errors.unauthorizedUpload()
//            // return this.consumeStreamAndReturnError(dockerRepo, tag, stream)
//        } else {
//            val var9: Response
//            try {
//                // lockId = (this.repo.getWorkContextC() as DockerWorkContext).obtainManifestLock("$dockerRepo/$tag")
//                val digest = this.processUploadedManifestType(dockerRepo, tag, manifestPath, manifestType, data)
//                return Response.status(201).header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Content-Digest", digest).build()
//            } catch (var14: DockerLockManifestException) {
//                // this.logException(var14)
//                var9 = DockerV2Errors.manifestConcurrent(var14.message!!)
//                return var9
//            } catch (var15: Exception) {
//                // this.logException(var15)
//                var9 = DockerV2Errors.manifestInvalid(var15.message!!)
//            } finally {
//                // IOUtils.closeQuietly(stream)
//                // this.releaseManifestLock(lockId, dockerRepo, tag)
//                // (this.repo.getWorkContextC() as DockerWorkContext).cleanup(this.repo.getRepoId(), "$dockerRepo/_uploads")
//            }
//
//            return var9
//        }
//    }

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
        log.info("digest : {}", digest)
        if (ManifestType.Schema2List.equals(manifestType)) {
            this.processManifestList(dockerRepo, tag, manifestPath, digest!!, manifestBytes, manifestType)
            return digest
        } else {
            val manifestMetadata = ManifestDeserializer.deserialize(this.repo, dockerRepo, tag, manifestType, manifestBytes, digest!!)
            this.addManifestsBlobs(manifestType, manifestBytes, manifestMetadata)
            if (!manifestSyncer.sync(this.repo, manifestMetadata, dockerRepo, tag)) {
                val msg = "Failed syncing manifest blobs, canceling manifest upload"
                log.error(msg)
                throw DockerSyncManifestException(msg)
            } else {
                log.info("start to upload manifest : {}", manifestType.toString())
                val response = this.repo.upload(this.manifestUploadContext(manifestType, manifestMetadata, manifestPath, manifestBytes))
                if (!this.uploadSuccessful(response)) {
                    throw IOException(response.toString())
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
            throw IOException(response.toString())
        }
    }

    private fun manifestListUploadContext(manifestType: ManifestType, digest: DockerDigest, manifestPath: String, manifestBytes: ByteArray): UploadContext {
        val context = UploadContext(manifestPath).content(ByteArrayInputStream(manifestBytes))
        if (manifestType.equals(ManifestType.Schema2List) && "sha256" == digest.getDigestAlg()) {
            context.sha256(digest.getDigestHex())
        }

        return context
    }

    private fun uploadSuccessful(response: ResponseEntity<Any>): Boolean {
        val status = response.statusCodeValue
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

    override fun isBlobExists(name: String, digest: DockerDigest): ResponseEntity<Any> {
        if (DockerSchemaUtils.isEmptyBlob(digest)) {
            log.debug("Request for empty layer for image {}, returning dummy HEAD response.", name)
            return DockerSchemaUtils.emptyBlobHeadResponse()
        } else {
            val blob = DockerUtils.getBlobFromRepoPath(this.repo, digest.filename(), name)
            if (blob != null) {
                val response = ResponseEntity.ok().header("Docker-Distribution-Api-Version", "registry/2.0")
                        .header("Docker-Content-Digest", digest.toString())
                        .header("Content-Length", blob.getLength().toString())
                        .header("Content-Type", "application/octet-stream").build<Any>()
                return response
            } else {
                return DockerV2Errors.blobUnknown(digest.toString())
            }
        }
    }

//    override fun getBlob(dockerRepo: String, digest: DockerDigest): Response {
//        log.info("Fetching docker blob '{}' from repo '{}'", digest, this.repo.getRepoId())
//        if (DockerSchemaUtils.isEmptyBlob(digest)) {
//            log.debug("Request for empty layer for image {}, returning dummy GET response.", dockerRepo)
//            return DockerSchemaUtils.emptyBlobGetResponse()
//        } else {
//            val blob = this.getRepoBlob(digest)
//            if (blob != null) {
//                val response = this.repo.download(DownloadContext(blob.getPath(), this.httpHeaders).header("artifactory.disableRedirect", "false"))
//                return Response.fromResponse(response).header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Content-Digest", digest).build()
//            } else {
//                return DockerV2Errors.blobUnknown(digest.toString())
//            }
//        }
//    }

    private fun getRepoBlob(digest: DockerDigest): Artifact? {
        var repoBlobs = this.repo.findArtifacts("", digest.filename())
        if (repoBlobs != null) {
            val var10001 = nonTempUploads
            var10001.javaClass
            // repoBlobs = Iterables.filter(repoBlobs, Predicate<T> { var10001.test(it) })
            val var3 = repoBlobs.iterator()

            while (var3.hasNext()) {
                val repoBlob = var3.next()
                val blobPath = repoBlob.getArtifactPath()
                if (this.repo.canRead(blobPath)) {
                    log.debug("Found repo blob at '{}'", blobPath)
                    return repoBlob
                }
            }
        }

        return null
    }

    override fun startBlobUpload(projectId: String, repoName: String, name: String, mount: String?): ResponseEntity<Any> {
        var dockerRepo = "/$projectId/$repoName/$name"
        val uploadDirectory = "$dockerRepo/_uploads"
        if (!this.repo.canWrite(uploadDirectory)) {
            return DockerV2Errors.unauthorizedUpload()
        } else {
            val location: URI
            if (mount != null) {
                var mountDigest = DockerDigest(mount)
                val mountableBlob = DockerUtils.getBlobGlobally(this.repo, mountDigest.filename(), DockerSearchBlobPolicy.SHA_256)
                if (mountableBlob != null) {
                    location = this.getDockerURI("$dockerRepo/blobs/$mount")
                    log.debug("Found accessible blob at {}/{} to mount onto {}", *arrayOf<Any>(mountableBlob.getRepoId(), mountableBlob.getArtifactPath(), this.repo.getRepoId() + "/" + dockerRepo + "/" + mount))
                    return ResponseEntity.status(201).header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Content-Digest", mount).header("Content-Length", "0").header("Location", location.toString()).build()
                }
            }

            val uuid = UUID.randomUUID().toString()
            location = this.getDockerURI("$dockerRepo/blobs/uploads/$uuid")
            return ResponseEntity.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Upload-Uuid", uuid).header("Location", location.toString()).build()
        }
    }

    private fun getDockerURI(path: String): URI {
        val hostHeaders = this.httpHeaders.get("Host")
        var host = ""
        var port: Int? = null
        if (hostHeaders != null && !hostHeaders.isEmpty()) {
            val parts = (hostHeaders[0] as String).split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            host = parts[0]
            if (parts.size > 1) {
                port = Integer.valueOf(parts[1])
            }
        } else {
            log.warn("Docker location URL is blank, make sure the Host request header exists.")
        }

        val headers = this.httpHeaders.entries
        val builder = UriBuilder.fromPath("v2/$path").host(host).scheme(this.getProtocol(this.httpHeaders))
        if (port != null) {
            builder.port(port)
        }

        val uri = builder.build(*arrayOfNulls(0))
        return (this.repo.getWorkContextC() as DockerWorkContext).rewriteRepoURI(this.repo.getRepoId(), uri, headers)
    }

    private fun getProtocol(httpHeaders: HttpHeaders): String {
        val protocolHeaders = httpHeaders.get("X-Forwarded-Proto")
        if (protocolHeaders != null && !protocolHeaders.isEmpty()) {
            return protocolHeaders.iterator().next() as String
        } else {
            log.debug("X-Forwarded-Proto does not exist, returning https.")
            return "https"
        }
    }

    fun uploadBlob(projectId: String,repoName: String,name: String, digest: DockerDigest, uuid: String, stream: InputStream): ResponseEntity<Any> {

        return if (this.putHasStream()) this.uploadBlobFromPut(projectId, repoName,name, digest, stream) else this.finishPatchUpload(projectId, repoName,name, digest, uuid)
    }

    private fun putHasStream(): Boolean {
        val headerValues = httpHeaders.get("User-Agent")
        if (headerValues != null) {
            val headerIter = headerValues!!.iterator()

            while (headerIter.hasNext()) {
                val userAgent = headerIter.next() as String
                log.debug("User agent header: {}", userAgent)
                if (OLD_USER_AGENT_PATTERN.matcher(userAgent).matches()) {
                    return true
                }
            }
        }

        return false
    }

    private fun uploadBlobFromPut(projectId: String,repoName: String,name: String, digest: DockerDigest, stream: InputStream): ResponseEntity<Any> {
        var dockerRepo = "/$projectId/$repoName/$name"
        val blobPath = dockerRepo + "/" + "_uploads" + "/"+digest.toString()
        if (!this.repo.canWrite(blobPath)) {
            return this.consumeStreamAndReturnError(dockerRepo, digest.toString(), stream)
        } else {
            log.info("Deploying docker blob '{}' into repo '{}'", blobPath, this.repo.getRepoId())
            var context = UploadContext(blobPath).content(stream).projectId(projectId).repoName(repoName).sha256(digest.getDigestHex())
            val response = this.repo.upload(context)
            if (response != null && this.uploadSuccessful(response)) {
                //this.repo.setAttribute(blobPath, digest.getDigestAlg(), digest.getDigestHex())
                val location = this.getDockerURI("$dockerRepo/blobs/$digest")
                return ResponseEntity.created(location).header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Content-Digest", digest.toString()).build()
            } else {
                log.warn("Error uploading blob '{}' got status '{}' and message: '{}'", *arrayOf(blobPath, response.statusCodeValue, response.toString()))
                return DockerV2Errors.blobUploadInvalid(response.toString())
            }
        }
    }

    private fun finishPatchUpload(projectId: String,repoName: String,name: String, digest: DockerDigest, uuid: String): ResponseEntity<Any> {
        var dockerRepo = "/$projectId/$repoName/$name"
        val uuidPath = "$dockerRepo/_uploads/$uuid"
        var context = UploadContext(dockerRepo).projectId(projectId).repoName(repoName)
        if (this.repo.exists(uuidPath)) {
            val blobPath = dockerRepo + "/" + "_uploads" + "/" + digest.filename()
            this.repo.uploadFromLocal(uuidPath, context)
            //(this.repo.getWorkContextC() as DockerWorkContext).setSystem()

            try {
                this.repo.deleteLocal(uuidPath)
            } finally {
                //(this.repo.getWorkContextC() as DockerWorkContext).unsetSystem()
            }

            //this.repo.setAttribute(blobPath, digest.getDigestAlg(), digest.getDigestHex())
            val location = this.getDockerURI("$dockerRepo/blobs/$digest")
            return ResponseEntity.created(location).header("Docker-Distribution-Api-Version", "registry/2.0").header("Content-Length", "0").header("Docker-Content-Digest", digest.toString()).build()
        } else {
            return DockerV2Errors.blobUnknown(digest.toString())
        }
    }

    fun patchUpload(projectId: String,repoName: String,name: String, uuid: String,request: HttpServletRequest): ResponseEntity<Any> {
        val stream = request.inputStream
        var dockerRepo = "/$projectId/$repoName/$name"
        val path  = "$dockerRepo/_uploads/"
        val blobPath = "$dockerRepo/_uploads/$uuid"
        if (!this.repo.canWrite(blobPath)) {
            return this.consumeStreamAndReturnError(blobPath, uuid, stream)
        } else {
            val response = this.repo.writeLocal(path, uuid ,stream)
            if (this.uploadSuccessful(response)) {
                val artifact = this.repo.artifact(blobPath)
                if (artifact != null) {
                    val location = this.getDockerURI("$dockerRepo/blobs/uploads/$uuid")
                    return ResponseEntity.status(202).header("Content-Length", "0").header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Upload-Uuid", uuid).header("Location", location.toString()).header("Range", "0-" + (artifact.getLength() - 1L)).build()
                }
            }

            log.warn("Error uploading blob '{}' got status '{}' and message: '{}'", *arrayOf<Any>(blobPath, response.statusCode, response.toString()))
            return DockerV2Errors.blobUploadInvalid(response.toString())
        }
    }

    private fun consumeStreamAndReturnError(dockerRepo: String, identifier: String, stream: InputStream): ResponseEntity<Any> {
        try {
            val output = NullOutputStream()
            var throwable: Throwable? = null
            try {
                IOUtils.copy(stream, output)
            } catch (throwablec: Throwable) {
                throwable = throwablec
                throw throwablec
            } finally {
                if (output != null) {
                    if (throwable != null) {
                        try {
                            output.close()
                        } catch (throwablecc: Throwable) {
                            throwable.addSuppressed(throwablecc)
                        }
                    } else {
                        output.close()
                    }
                }
            }
        } catch (exception: IOException) {
            log.debug("Failed to consume incoming stream for " + this.repo.getRepoId() + ":" + dockerRepo + "/" + identifier, exception)
        } finally {
            IOUtils.closeQuietly(stream)
        }
        return DockerV2Errors.unauthorizedUpload()
    }

     fun testUpload(stream: InputStream): ResponseEntity<Any> {
         var context :UploadContext = UploadContext()
         context.repoName = "docker-local"
         context.path = "/data/ops/docker-local"
         context.projectId = "ops"
         context.content = stream
         context.sha256 = "f68ca2a4f15ce42ff9a8ac469f7c6447d42655afd7ba50d995507525fff18d08"
         return this.repo.upload(context)
    }
}
