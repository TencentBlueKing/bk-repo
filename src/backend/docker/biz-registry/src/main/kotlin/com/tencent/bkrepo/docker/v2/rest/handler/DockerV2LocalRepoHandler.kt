package com.tencent.bkrepo.docker.v2.rest.handler

import com.fasterxml.jackson.databind.JsonNode
import com.google.common.base.Joiner
import com.tencent.bkrepo.docker.DockerWorkContext
import com.tencent.bkrepo.docker.artifact.repomd.DockerArtifactoryService
import com.tencent.bkrepo.docker.exception.DockerLockManifestException
import com.tencent.bkrepo.docker.exception.DockerNotFoundException
import com.tencent.bkrepo.docker.exception.DockerSyncManifestException
import com.tencent.bkrepo.docker.manifest.ManifestDeserializer
import com.tencent.bkrepo.docker.manifest.ManifestJson
import com.tencent.bkrepo.docker.manifest.ManifestListSchema2Deserializer
import com.tencent.bkrepo.docker.manifest.ManifestType
import com.tencent.bkrepo.docker.repomd.Artifact
import com.tencent.bkrepo.docker.repomd.DownloadContext
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
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.apache.commons.lang.StringUtils
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.*
import java.util.function.Predicate
import java.util.regex.Pattern
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriBuilder
import kotlin.collections.HashMap
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.NullOutputStream
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.stream.StreamSupport
import org.springframework.http.MediaType
import kotlin.streams.toList

@Service
class DockerV2LocalRepoHandler @Autowired constructor(
        private val repo: DockerArtifactoryService
) : DockerV2RepoHandler {

    var httpHeaders: HttpHeaders = HttpHeaders()

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

    override fun ping(): ResponseEntity<Any> {
        return ResponseEntity.ok().header("Content-Type", "application/json").header("Docker-Distribution-Api-Version", "registry/2.0").body("{}")
    }

    override fun getManifest(projectId: String, repoName: String, dockerRepo: String, reference: String): ResponseEntity<Any> {
        try {
            val digest = DockerDigest(reference)
            return this.getManifestByDigest(projectId, repoName, dockerRepo, digest)
        } catch (exception: Exception) {
            log.trace("Unable to parse digest, fetching manifest by tag '{}'", reference)
            return this.getManifestByTag(projectId, repoName, dockerRepo, reference)
        }
    }

    private fun getManifestByDigest(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest): ResponseEntity<Any> {
        log.info("Fetching docker manifest for repo '{}' and digest '{}' in repo '{}'", dockerRepo, digest, repoName)
        var matched = this.findMatchingArtifacts(projectId, repoName, dockerRepo, digest, "manifest.json")
        if (matched == null) {
            val acceptable = this.getAcceptableManifestTypes()
            if (acceptable.contains(ManifestType.Schema2List)) {
                matched = this.findMatchingArtifacts(projectId, repoName, dockerRepo, digest, "list.manifest.json")
            }
        }

        return if (matched == null) DockerV2Errors.manifestUnknown(digest.toString()) else this.buildManifestResponse(projectId, repoName, dockerRepo, digest, matched)
    }

    private fun findMatchingArtifacts(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest, filename: String): Artifact? {
        var nodeDetail = this.repo.findArtifact(projectId, repoName, dockerRepo, filename)
        if (nodeDetail == null) {
            return null
        }
        var sha256 = nodeDetail.nodeInfo.sha256
        return Artifact(projectId, repoName, dockerRepo).sha256(sha256.toString())
    }

    private fun getAcceptableManifestTypes(): List<ManifestType> {
        return this.httpHeaders.getAccept().stream().filter { Objects.nonNull(it) }.map { ManifestType.from(it) }.toList()
    }

    private fun getManifestByTag(projectId: String, repoName: String, dockerRepo: String, tag: String): ResponseEntity<Any> {
        val useManifestType = this.chooseManifestType(projectId, repoName, dockerRepo, tag)
        val manifestPath = buildManifestPathFromType(dockerRepo, tag, useManifestType)
        if (!this.repo.canRead(manifestPath)) {
            return DockerV2Errors.unauthorizedManifest(manifestPath, null as String?)
        } else if (!this.repo.exists(projectId, repoName, manifestPath)) {
            return DockerV2Errors.manifestUnknown(manifestPath)
        } else {
            var manifest = this.repo.findManifest(projectId, repoName, manifestPath)
            if (manifest == null) {
                return DockerV2Errors.manifestUnknown(manifestPath)
            } else {
                val artifact = Artifact(projectId,repoName,manifestPath).sha256(manifest.nodeInfo.sha256!!)
                return this.buildManifestResponse(projectId, repoName, manifestPath, DockerDigest("sh256:${manifest.nodeInfo.sha256}"),artifact)
            }
        }
        return DockerV2Errors.manifestUnknown(manifestPath)
    }

    private fun chooseManifestType(projectId: String, repoName: String, dockerRepo: String, tag: String): ManifestType {
        val acceptable = this.getAcceptableManifestTypes()
        if (acceptable.contains(ManifestType.Schema2List)) {
            val manifestPath = buildManifestPathFromType(dockerRepo, tag, ManifestType.Schema2List)
            if (this.repo.exists(projectId, repoName, manifestPath)) {
                return ManifestType.Schema2List
            }
        }

        return if (acceptable.contains(ManifestType.Schema2)) {
            ManifestType.Schema2
        } else if (acceptable.contains(ManifestType.Schema1Signed)) {
            ManifestType.Schema1Signed
        } else {
            if (acceptable.contains(ManifestType.Schema1)) ManifestType.Schema1 else ManifestType.Schema1Signed
        }
    }

    private fun buildManifestResponse(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest, manifest: Artifact): ResponseEntity<Any> {
        var context = DownloadContext(projectId, repoName, dockerRepo).projectId(projectId).repoName(repoName).sha256(digest.getDigestHex())
        var file = this.repo.download(context)
        val inputStreamResource = InputStreamResource(file.inputStream())
        httpHeaders.contentLength = file.length()
        httpHeaders.set("Docker-Distribution-Api-Version", "registry/2.0")
        httpHeaders.set("Docker-Content-Digest", digest.toString())
        httpHeaders.set("Content-Type", DockerSchemaUtils.getManifestType(projectId,repoName, dockerRepo, this.repo))
        return ResponseEntity(inputStreamResource, httpHeaders, HttpStatus.OK)
    }

    override fun deleteManifest(projectId: String, repoName: String, dockerRepo: String, reference: String): ResponseEntity<Any> {
        try {
            val digest = DockerDigest(reference)
            return this.deleteManifestByDigest(projectId, repoName, dockerRepo, DockerDigest(reference))
        } catch (var4: Exception) {
            log.trace("Unable to parse digest, deleting manifest by tag '{}'", reference)
            return this.deleteManifestByTag(projectId, repoName, dockerRepo, reference)
        }
    }

    private fun deleteManifestByDigest(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest): ResponseEntity<Any> {
        log.info("Deleting docker manifest for repo '{}' and digest '{}' in repo '{}'", dockerRepo, digest, repoName)
//        val manifests = this.repo.findArtifacts(dockerRepo, "manifest.json")
//        val manifestIter = manifests.iterator()
//
//        while (manifestIter.hasNext()) {
//            val manifest = manifestIter.next()
//            if (this.repo.canWrite(manifest.path)) {
//                val manifestDigest = this.repo.getAttribute(projectId ,repoName ,manifest.path, digest.getDigestAlg())
//                if (StringUtils.isNotBlank(manifestDigest) && StringUtils.equals(manifestDigest, digest.getDigestHex()) && this.repo.delete(manifest.path)) {
//                    return ResponseEntity.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").build()
//                }
//            }
//        }

        return DockerV2Errors.manifestUnknown(digest.toString())
    }

    private fun deleteManifestByTag(projectId: String, repoName: String, dockerRepo: String, tag: String): ResponseEntity<Any> {
        val tagPath = "$dockerRepo/$tag"
        val manifestPath = "$tagPath/manifest.json"
        if (!this.repo.exists(projectId, repoName, manifestPath)) {
            return DockerV2Errors.manifestUnknown(manifestPath)
        } else if (this.repo.delete(tagPath)) {
            return ResponseEntity.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").build()
        } else {
            log.warn("Unable to delete tag '{}'", manifestPath)
            return DockerV2Errors.manifestUnknown(manifestPath)
        }
    }

    override fun uploadManifest(projectId: String, repoName: String, dockerRepo: String, tag: String, mediaType: String, stream: InputStream): ResponseEntity<Any> {
        log.info("Deploying docker manifest for repo '{}' and tag '{}' into repo '{}' mediatype", dockerRepo, tag, repoName, mediaType)
        val manifestType = ManifestType.from(mediaType)
        val manifestPath = buildManifestPathFromType(dockerRepo, tag, manifestType)
        log.info("manifest path to {} .", manifestPath)
        if (!this.repo.canWrite(manifestPath)) {
            log.debug("Attempt to write manifest to {} failed the permission check.", manifestPath)
            return this.consumeStreamAndReturnError(dockerRepo, tag, stream)
        } else {
            stream.use {
                val digest = this.processUploadedManifestType(projectId, repoName, dockerRepo, tag, manifestPath, manifestType, it)
                this.repo.getWorkContextC().cleanup(repoName, "$dockerRepo/_uploads")
                return ResponseEntity.status(201).header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Content-Digest", digest.toString()).build()
            }
        }
    }

    private fun buildManifestPathFromType(dockerRepo: String, tag: String, manifestType: ManifestType): String {
        val manifestPath: String
        if (ManifestType.Schema2List == manifestType) {
            manifestPath = "/$dockerRepo/$tag/list.manifest.json"
        } else {
            manifestPath = "/$dockerRepo/$tag/manifest.json"
        }

        return manifestPath
    }

    @Throws(IOException::class, DockerSyncManifestException::class)
    private fun processUploadedManifestType(projectId: String, repoName: String, dockerRepo: String, tag: String, manifestPath: String, manifestType: ManifestType, stream: InputStream): DockerDigest {
        val manifestBytes = IOUtils.toByteArray(stream)
        val digest = DockerManifestDigester.calc(manifestBytes)
        log.info("digest : {}", digest)
        if (ManifestType.Schema2List == manifestType) {
            this.processManifestList(projectId, repoName, dockerRepo, tag, manifestPath, digest!!, manifestBytes, manifestType)
            return digest
        } else {
            val manifestMetadata = ManifestDeserializer.deserialize(this.repo, projectId, repoName, dockerRepo, tag, manifestType, manifestBytes, digest!!)
            this.addManifestsBlobs(manifestType, manifestBytes, manifestMetadata)
            if (!manifestSyncer.sync(this.repo, manifestMetadata, projectId, repoName, dockerRepo, tag)) {
                val msg = "Failed syncing manifest blobs, canceling manifest upload"
                log.error(msg)
                throw DockerSyncManifestException(msg)
            } else {
                log.info("start to upload manifest : {}", manifestType.toString())
                val response = this.repo.upload(this.manifestUploadContext(projectId, repoName, manifestType, manifestMetadata, manifestPath, manifestBytes))
                if (!this.uploadSuccessful(response)) {
                    throw IOException(response.toString())
                } else {
                    val params = this.buildManifestPropertyMap(dockerRepo, tag, digest, manifestType)
                    val labels = manifestMetadata.tagInfo.labels
                    labels.entries().forEach {
                        params.set(it.key, it.value)
                    }
                    this.repo.setAttributes(projectId, repoName, manifestPath, params)
                    this.repo.getWorkContextC().onTagPushedSuccessfully(repoName, dockerRepo, tag)
                    return digest
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun processManifestList(projectId: String, repoName: String, dockerRepo: String, tag: String, manifestPath: String, digest: DockerDigest, manifestBytes: ByteArray, manifestType: ManifestType) {
        val manifestList = ManifestListSchema2Deserializer.deserialize(manifestBytes)
        if (manifestList != null) {
            val iter = manifestList.manifests.iterator()
            // check every manifest in the repo
            while (iter.hasNext()) {
                val manifest = iter.next() as ManifestJson
                val mDigest = manifest.digest
                val manifestFilename = DockerDigest(mDigest!!).filename()
                val manifestFile = DockerUtils.findBlobGlobally(this.repo, projectId, repoName, dockerRepo, manifestFilename)
                if (manifestFile == null) {
                    throw DockerNotFoundException("Manifest list (" + digest.toString() + ") Missing manifest digest " + mDigest + ". ==>" + manifest.toString())
                }
            }
        }

        val response = this.repo.upload(this.manifestListUploadContext(projectId, repoName, manifestType, digest, manifestPath, manifestBytes))
        if (this.uploadSuccessful(response)) {
            val params = this.buildManifestPropertyMap(dockerRepo, tag, digest, manifestType)
            this.repo.setAttributes(projectId, repoName, manifestPath, params)
            this.repo.getWorkContextC().onTagPushedSuccessfully(repoName, dockerRepo, tag)
        } else {
            throw IOException(response.toString())
        }
    }

    private fun manifestListUploadContext(projectId: String, repoName: String, manifestType: ManifestType, digest: DockerDigest, manifestPath: String, manifestBytes: ByteArray): UploadContext {
        val context = UploadContext(projectId, repoName, manifestPath).content(ByteArrayInputStream(manifestBytes))
        if (manifestType.equals(ManifestType.Schema2List) && "sha256" == digest.getDigestAlg()) {
            context.sha256(digest.getDigestHex())
        }

        return context
    }

    private fun uploadSuccessful(response: ResponseEntity<Any>): Boolean {
        val status = response.statusCodeValue
        return status == Response.Status.OK.statusCode || status == Response.Status.CREATED.statusCode
    }

    private fun buildManifestPropertyMap(dockerRepo: String, tag: String, digest: DockerDigest, manifestType: ManifestType): HashMap<String, String> {
        var map = HashMap<String, String>()
        map.set(digest.getDigestAlg(), digest.getDigestHex())
        map.set("docker.manifest.digest", digest.toString())
        map.set("docker.manifest", tag)
        map.set("docker.repoName", dockerRepo)
        map.set("docker.manifest.type", manifestType.toString())
        return map
    }

    private fun releaseManifestLock(lockId: String, dockerRepo: String, tag: String) {
        try {
            this.repo.getWorkContextC().releaseManifestLock(lockId, "$dockerRepo/$tag")
        } catch (exception: Exception) {
            log.error("Error uploading manifest: '{}'", exception.message)
        }
    }

    @Throws(IOException::class)
    private fun addManifestsBlobs(manifestType: ManifestType, manifestBytes: ByteArray, manifestMetadata: ManifestMetadata) {
        if (ManifestType.Schema2 == manifestType) {
            this.addSchema2Blob(manifestBytes, manifestMetadata)
        } else if (ManifestType.Schema2List == manifestType) {
            this.addSchema2ListBlobs(manifestBytes, manifestMetadata)
        }
    }

    @Throws(IOException::class)
    private fun addSchema2Blob(manifestBytes: ByteArray, manifestMetadata: ManifestMetadata) {
        val manifest = JsonUtil.readTree(manifestBytes)
        val config = manifest.get("config")
        if (config != null) {
            val digest = config.get("digest").asText()
            val blobInfo = DockerBlobInfo("", digest, 0L, "")
            manifestMetadata.blobsInfo.add(blobInfo)
        }
    }

    @Throws(IOException::class)
    private fun addSchema2ListBlobs(manifestBytes: ByteArray, manifestMetadata: ManifestMetadata) {
        val manifestList = JsonUtil.readTree(manifestBytes)
        if (manifestList != null) {
            val manifests = manifestList.get("manifests")
            val manifest = manifests.iterator()

            while (manifest.hasNext()) {
                val manifestNode = manifest.next() as JsonNode
                val digest = manifestNode.get("platform").get("digest").asText()
                val dockerBlobInfo = DockerBlobInfo("", digest, 0L, "")
                manifestMetadata.blobsInfo.add(dockerBlobInfo)
                val manifestFilename = DockerDigest(digest).filename()
                val manifestFile = DockerUtils.getBlobGlobally(this.repo, manifestFilename, DockerSearchBlobPolicy.SHA_256)
                if (manifestFile != null) {
                    val configBytes = DockerSchemaUtils.fetchSchema2Manifest(this.repo, DockerUtils.getFullPath(manifestFile, this.repo.getWorkContextC()))
                    this.addSchema2Blob(configBytes, manifestMetadata)
                }
            }
        }
    }

    private fun manifestUploadContext(projectId: String, repoName: String, manifestType: ManifestType, manifestMetadata: ManifestMetadata, manifestPath: String, manifestBytes: ByteArray): UploadContext {
        val context = UploadContext(projectId, repoName, manifestPath).content(ByteArrayInputStream(manifestBytes))
        if ((manifestType.equals(ManifestType.Schema2) || manifestType.equals(ManifestType.Schema2List)) && manifestMetadata.tagInfo != null && manifestMetadata.tagInfo.digest != null && "sha256" == manifestMetadata.tagInfo.digest.getDigestAlg()) {
            context.sha256(manifestMetadata.tagInfo.digest.getDigestHex())
        }

        return context
    }

    override fun isBlobExists(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest): ResponseEntity<Any> {
        log.info("is blob exist upload {}, {},{},{}", projectId,repoName, dockerRepo, digest.getDigestHex())
        if (DockerSchemaUtils.isEmptyBlob(digest)) {
            log.debug("Request for empty layer for image {}, returning dummy HEAD response.", dockerRepo)
            return DockerSchemaUtils.emptyBlobHeadResponse()
        } else {
            val blob = DockerUtils.getBlobFromRepoPath(this.repo, projectId, repoName, dockerRepo, digest.filename())
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

    override fun getBlob(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest): ResponseEntity<Any> {
        log.info("Fetching docker blob '{}' from repo '{}'", digest, repoName)
        if (DockerSchemaUtils.isEmptyBlob(digest)) {
            log.debug("Request for empty layer for image {}, returning dummy GET response.", dockerRepo)
            return DockerSchemaUtils.emptyBlobGetResponse()
        } else {
            val blob = this.getRepoBlob(projectId, repoName, dockerRepo, digest)
            if (blob != null) {
                var context = DownloadContext(projectId, repoName, dockerRepo).projectId(projectId).repoName(repoName).sha256(digest.getDigestHex())
                var file = this.repo.download(context)
                val inputStreamResource = InputStreamResource(file.inputStream())
                httpHeaders.contentLength = file.length()
                httpHeaders.set("Docker-Distribution-Api-Version", "registry/2.0")
                httpHeaders.set("Docker-Content-Digest", digest.toString())
                return ResponseEntity(inputStreamResource, httpHeaders, HttpStatus.OK)
            } else {
                return DockerV2Errors.blobUnknown(digest.toString())
            }
        }
    }

    private fun getRepoBlob(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest): Artifact? {
        val result = this.repo.findArtifacts(projectId,repoName, digest.filename())
        if (result.size == 0) {
            return null
        }
        return Artifact(projectId, repoName, dockerRepo).sha256(digest.filename())
    }

    override fun startBlobUpload(projectId: String, repoName: String, dockerRepo: String, mount: String?): ResponseEntity<Any> {
        val uploadDirectory = "$dockerRepo/_uploads"
        log.info("start upload {}", dockerRepo)
        if (!this.repo.canWrite(uploadDirectory)) {
            return DockerV2Errors.unauthorizedUpload()
        } else {
            val location: URI
            if (mount != null) {
                var mountDigest = DockerDigest(mount)
                val mountableBlob = DockerUtils.findBlobGlobally(this.repo, projectId, repoName, dockerRepo, mountDigest.filename())
                if (mountableBlob != null) {
                    location = this.getDockerURI(repoName, "$dockerRepo/blobs/$mount")
                    log.debug("Found accessible blob at {}/{} to mount onto {}", mountableBlob.repoName, mountableBlob.path, repoName + "/" + dockerRepo + "/" + mount)
                    return ResponseEntity.status(201).header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Content-Digest", mount).header("Content-Length", "0").header("Location", location.toString()).build()
                }
            }

            val uuid = UUID.randomUUID().toString()
            location = this.getDockerURI(repoName, "$projectId/$repoName/$dockerRepo/blobs/uploads/$uuid")
            return ResponseEntity.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Upload-Uuid", uuid).header("Location", location.toString()).build()
        }
    }

    private fun getDockerURI(repoName: String, path: String): URI {
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

        val builder = UriBuilder.fromPath("v2/$path").host(host).scheme(this.getProtocol(this.httpHeaders))
        if (port != null) {
            builder.port(port)
        }

        val uri = builder.build(*arrayOfNulls(0))
        return this.repo.getWorkContextC().rewriteRepoURI(repoName, uri, this.httpHeaders.entries)
    }

    private fun getProtocol(httpHeaders: HttpHeaders): String {
        val protocolHeaders = httpHeaders.get("X-Forwarded-Proto")
        return "http"
        if (protocolHeaders != null && !protocolHeaders.isEmpty()) {
            return protocolHeaders.iterator().next() as String
        } else {
            log.debug("X-Forwarded-Proto does not exist, returning https.")
            return "https"
        }
    }

    override fun uploadBlob(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest, uuid: String, stream: InputStream): ResponseEntity<Any> {
        return if (this.putHasStream()) this.uploadBlobFromPut(projectId, repoName, dockerRepo, digest, stream) else this.finishPatchUpload(projectId, repoName, dockerRepo, digest, uuid)
    }

    private fun putHasStream(): Boolean {
        val headerValues = httpHeaders.get("User-Agent")
        if (headerValues != null) {
            val headerIter = headerValues.iterator()

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

    private fun uploadBlobFromPut(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest, stream: InputStream): ResponseEntity<Any> {
        val blobPath = dockerRepo + "/" + "_uploads" + "/" + digest.filename()
        if (!this.repo.canWrite(blobPath)) {
            return this.consumeStreamAndReturnError(dockerRepo, digest.toString(), stream)
        } else {
            log.info("Deploying docker blob '{}' into repo '{}'", blobPath, repoName)
            var context = UploadContext(projectId, repoName, blobPath).content(stream).projectId(projectId).repoName(repoName).sha256(digest.getDigestHex())
            val response = this.repo.upload(context)
            if (this.uploadSuccessful(response)) {
                val location = this.getDockerURI(repoName, "$dockerRepo/blobs/$digest")
                return ResponseEntity.created(location).header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Content-Digest", digest.toString()).build()
            } else {
                log.warn("Error uploading blob '{}' got status '{}' and message: '{}'", *arrayOf(blobPath, response.statusCodeValue, response.toString()))
                return DockerV2Errors.blobUploadInvalid(response.toString())
            }
        }
    }

    private fun finishPatchUpload(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest, uuid: String): ResponseEntity<Any> {
        log.info("finish upload {}", digest)
        val uuidPath = "$dockerRepo/_uploads/$uuid"
        val fileName = digest.filename()
        if (this.repo.existsLocal(projectId, repoName, uuidPath)) {
            val blobPath = "/$dockerRepo/_uploads/$fileName"
            var context = UploadContext(projectId, repoName, blobPath)
            this.repo.uploadFromLocal(uuidPath, context)
            this.repo.getWorkContextC().setSystem()

            try {
                this.repo.deleteLocal(projectId, repoName, uuidPath)
            } finally {
                this.repo.getWorkContextC().unsetSystem()
            }

            val location = this.getDockerURI(repoName, "$projectId/$repoName/$dockerRepo/blobs/$digest")
            return ResponseEntity.created(location).header("Docker-Distribution-Api-Version", "registry/2.0").header("Content-Length", "0").header("Docker-Content-Digest", digest.toString()).build()
        } else {
            return DockerV2Errors.blobUnknown(digest.toString())
        }
    }

    override fun patchUpload(projectId: String, repoName: String, dockerRepo: String, uuid: String, request: HttpServletRequest): ResponseEntity<Any> {
        log.info("patch upload {}", uuid)
        val stream = request.inputStream
        val path = "$dockerRepo/_uploads/"
        val blobPath = "$dockerRepo/_uploads/$uuid"
        if (!this.repo.canWrite(blobPath)) {
            return this.consumeStreamAndReturnError(blobPath, uuid, stream)
        } else {
            val response = this.repo.writeLocal(projectId, repoName, path, uuid, stream)
            if (this.uploadSuccessful(response)) {
                val artifact = this.repo.artifactLocal(projectId, repoName, blobPath)
                if (artifact != null) {
                    val location = this.getDockerURI(repoName, "$projectId/$repoName/$dockerRepo/blobs/uploads/$uuid")
                    return ResponseEntity.status(202).header("Content-Length", "0").header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Upload-Uuid", uuid).header("Location", location.toString()).header("Range", "0-" + (artifact.getLength() - 1L)).build()
                }
            }

            log.warn("Error uploading blob '{}' got status '{}' and message: '{}'", *arrayOf<Any>(blobPath, response.statusCode, response.toString()))
            return DockerV2Errors.blobUploadInvalid(response.toString())
        }
    }

    private fun consumeStreamAndReturnError(dockerRepo: String, identifier: String, stream: InputStream): ResponseEntity<Any> {
        NullOutputStream().use {
            IOUtils.copy(stream, it)
        }
        return DockerV2Errors.unauthorizedUpload()
    }

    private fun logException(e: Exception) {
        log.error("Error uploading manifest: '{}'", e.message)
        log.debug("Error uploading manifest: ", e)
    }
}
