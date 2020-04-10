package com.tencent.bkrepo.docker.service

import com.fasterxml.jackson.databind.JsonNode
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.docker.artifact.Artifact
import com.tencent.bkrepo.docker.artifact.DockerArtifactoryService
import com.tencent.bkrepo.docker.context.DownloadContext
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.errors.DockerV2Errors
import com.tencent.bkrepo.docker.exception.DockerNotFoundException
import com.tencent.bkrepo.docker.exception.DockerSyncManifestException
import com.tencent.bkrepo.docker.helpers.DockerCatalogTagsSlicer
import com.tencent.bkrepo.docker.helpers.DockerManifestDigester
import com.tencent.bkrepo.docker.helpers.DockerManifestSyncer
import com.tencent.bkrepo.docker.helpers.DockerPaginationElementsHolder
import com.tencent.bkrepo.docker.helpers.DockerSearchBlobPolicy
import com.tencent.bkrepo.docker.manifest.ManifestDeserializer
import com.tencent.bkrepo.docker.manifest.ManifestListSchema2Deserializer
import com.tencent.bkrepo.docker.manifest.ManifestType
import com.tencent.bkrepo.docker.model.DockerBasicPath
import com.tencent.bkrepo.docker.model.DockerBlobInfo
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata
import com.tencent.bkrepo.docker.response.CatalogResponse
import com.tencent.bkrepo.docker.response.TagsResponse
import com.tencent.bkrepo.docker.util.DockerSchemaUtils
import com.tencent.bkrepo.docker.util.DockerUtils
import com.tencent.bkrepo.docker.util.JsonUtil
import com.tencent.bkrepo.docker.util.RepoUtil
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.Objects
import java.util.regex.Pattern
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriBuilder
import kotlin.collections.HashMap
import kotlin.streams.toList
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.NullOutputStream
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class DockerV2LocalRepoService @Autowired constructor(val repo: DockerArtifactoryService) : DockerV2RepoService {

    var httpHeaders: HttpHeaders = HttpHeaders()

    lateinit var userId: String

    companion object {
        private val manifestSyncer = DockerManifestSyncer()
        private val logger = LoggerFactory.getLogger(DockerV2LocalRepoService::class.java)
        private val OLD_USER_AGENT_PATTERN = Pattern.compile("^(?:docker\\/1\\.(?:3|4|5|6|7(?!\\.[0-9]-dev))|Go ).*$")
    }

    override fun ping(): ResponseEntity<Any> {
        return ResponseEntity.ok().header("Content-Type", "application/json")
            .header("Docker-Distribution-Api-Version", "registry/2.0").body("{}")
    }

    override fun getTags(
        projectId: String,
        repoName: String,
        dockerRepo: String,
        maxEntries: Int,
        lastEntry: String
    ): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, projectId, repoName)
        val elementsHolder = DockerPaginationElementsHolder()
        val manifests = this.repo.findArtifacts(projectId, repoName, "manifest.json")

        if (manifests.size != 0) {
            manifests.forEach {
                var path = it["path"] as String
                val tagName = path.replaceAfterLast("/", "").removeSuffix("/").removePrefix("/" + dockerRepo + "/")
                elementsHolder.elements.add(tagName)
            }

            if (elementsHolder.elements.isEmpty()) {
                return DockerV2Errors.nameUnknown(dockerRepo)
            } else {
                DockerCatalogTagsSlicer.sliceCatalog(elementsHolder, maxEntries, lastEntry)
                val shouldAddLinkHeader = elementsHolder.hasMoreElements
                val tagsResponse = TagsResponse(elementsHolder, dockerRepo)
                httpHeaders.set("Docker-Distribution-Api-Version", "registry/2.0")
                if (shouldAddLinkHeader) {
                    httpHeaders.set(
                        "Link",
                        "</v2/" + dockerRepo + "/tags/list?last=" + tagsResponse.tags.last() as String + "&n=" + maxEntries + ">; rel=\"next\""
                    )
                }

                return ResponseEntity(tagsResponse, httpHeaders, HttpStatus.OK)
            }
        } else {
            return DockerV2Errors.nameUnknown(dockerRepo)
        }
    }

    override fun catalog(projectId: String, name: String, maxEntries: Int, lastEntry: String): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, projectId, name)
        val manifests = this.repo.findArtifacts(projectId, name, "manifest.json")
        val elementsHolder = DockerPaginationElementsHolder()

        manifests.forEach {
            val path = it["path"] as String
            val repoName = path.replaceAfterLast("/", "").replaceAfterLast("/", "").removeSuffix("/")

            if (StringUtils.isNotBlank(repoName)) {
                elementsHolder.addElement(repoName)
            }
            DockerCatalogTagsSlicer.sliceCatalog(elementsHolder, maxEntries, lastEntry)
        }
        val shouldAddLinkHeader = elementsHolder.hasMoreElements
        val catalogResponse = CatalogResponse(elementsHolder)
        httpHeaders.set("Docker-Distribution-Api-Version", "registry/2.0")
        if (shouldAddLinkHeader) {
            httpHeaders.set(
                "Link",
                "</v2/_catalog?last=" + catalogResponse.repositories.last() as String + "&n=" + maxEntries + ">; rel=\"next\""
            )
        }
        return ResponseEntity(catalogResponse, httpHeaders, HttpStatus.OK)
    }

    override fun getManifest(
        path: DockerBasicPath,
        reference: String
    ): ResponseEntity<Any> {
        logger.info("get manifest params {} , {} ,{} ,{} ", path.projectId, path.repoName, path.dockerRepo, reference)
        RepoUtil.loadRepo(repo, userId, path.projectId, path.repoName)
        try {
            val digest = DockerDigest(reference)
            return this.getManifestByDigest(path, digest)
        } catch (exception: Exception) {
            logger.trace("unable to parse digest, fetching manifest by tag '{}'", reference)
            return this.getManifestByTag(path, reference)
        }
    }

    private fun getManifestByDigest(
        path: DockerBasicPath,
        digest: DockerDigest
    ): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, path.projectId, path.repoName)
        logger.info("fetch docker manifest {} and digest {} in repo {}", path.dockerRepo, digest, path.repoName)
        var matched = this.findMatchingArtifacts(path, "manifest.json")
        if (matched == null) {
            val acceptable = this.getAcceptableManifestTypes()
            if (acceptable.contains(ManifestType.Schema2List)) {
                matched = this.findMatchingArtifacts(path, "list.manifest.json")
            }
        }

        if (matched == null) {
            return DockerV2Errors.manifestUnknown(digest.toString())
        } else {
            return this.buildManifestResponse(path, path.dockerRepo, digest)
        }
    }

    private fun findMatchingArtifacts(
        path: DockerBasicPath,
        filename: String
    ): Artifact? {
        var nodeDetail = this.repo.findArtifact(path, filename)
        if (nodeDetail == null) {
            return null
        }
        var sha256 = nodeDetail.nodeInfo.sha256
        return Artifact(path.projectId, path.repoName, path.dockerRepo).sha256(sha256.toString())
    }

    private fun getAcceptableManifestTypes(): List<ManifestType> {
        return this.httpHeaders.getAccept().stream().filter { Objects.nonNull(it) }.map { ManifestType.from(it) }
            .toList()
    }

    private fun getManifestByTag(
        path: DockerBasicPath,
        tag: String
    ): ResponseEntity<Any> {
        val useManifestType = this.chooseManifestType(path, tag)
        val manifestPath = buildManifestPathFromType(path.dockerRepo, tag, useManifestType)
        logger.info("get manifest by tag params {} ,{} ,{} ", path.projectId, path.repoName, manifestPath)
        if (!this.repo.canRead(path)) {
            return DockerV2Errors.unauthorizedManifest(manifestPath, null as String?)
        } else if (!this.repo.exists(path.projectId, path.repoName, manifestPath)) {
            return DockerV2Errors.manifestUnknown(manifestPath)
        } else {
            var manifest = this.repo.findManifest(path.projectId, path.repoName, manifestPath)
            if (manifest == null) {
                return DockerV2Errors.manifestUnknown(manifestPath)
            } else {
                return this.buildManifestResponse(path, manifestPath, DockerDigest("sh256:${manifest.nodeInfo.sha256}"))
            }
        }
    }

    private fun chooseManifestType(path: DockerBasicPath, tag: String): ManifestType {
        val acceptable = this.getAcceptableManifestTypes()
        if (acceptable.contains(ManifestType.Schema2List)) {
            val manifestPath = buildManifestPathFromType(path.dockerRepo, tag, ManifestType.Schema2List)
            if (this.repo.exists(path.projectId, path.repoName, manifestPath)) {
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

    fun getManifestString(
        path: DockerBasicPath,
        tag: String
    ): String {
        val useManifestType = this.chooseManifestType(path, tag)
        val manifestPath = buildManifestPathFromType(path.dockerRepo, tag, useManifestType)
        var manifest = this.repo.findManifest(path.projectId, path.repoName, manifestPath)
        if (manifest == null) {
            logger.info("node not exist {}, {},{}", path.projectId, path.repoName, manifestPath)
            return ""
        } else {
            var context = DownloadContext(path.projectId, path.repoName, path.dockerRepo).projectId(path.projectId)
                .repoName(path.repoName)
                .sha256(manifest.nodeInfo.sha256!!)
            var file = this.repo.download(context)
            val contents = file.readText()
            return contents
        }
    }

    fun getRepoList(projectId: String, repoName: String): List<String> {
        return this.repo.findRepoList(projectId, repoName)
    }

    fun getRepoTagList(
        projectId: String,
        repoName: String,
        image: String
    ): Map<String, String> {
        return this.repo.findRepoTagList(projectId, repoName, image)
    }

    fun buildLayerResponse(
        projectId: String,
        repoName: String,
        dockerRepo: String,
        id: String
    ): ResponseEntity<Any> {
        val digest = DockerDigest(id)
        var context = DownloadContext(projectId, repoName, dockerRepo).projectId(projectId).repoName(repoName)
            .sha256(digest.getDigestHex())
        var file = this.repo.download(context)
        val inputStreamResource = InputStreamResource(file.inputStream())
        httpHeaders.set("Docker-Distribution-Api-Version", "registry/2.0")
        httpHeaders.set("Docker-Content-Digest", digest.toString())
        httpHeaders.set("Content-Type", DockerSchemaUtils.getManifestType(projectId, repoName, dockerRepo, this.repo))
        logger.info("file result length {}", file.length())
        return ResponseEntity.ok()
            .headers(httpHeaders)
            .contentLength(file.length())
            .body(inputStreamResource)
    }

    private fun buildManifestResponse(
        path: DockerBasicPath,
        manifestPath: String,
        digest: DockerDigest
    ): ResponseEntity<Any> {
        var context = DownloadContext(path.projectId, path.repoName, path.dockerRepo).projectId(path.projectId)
            .repoName(path.repoName)
            .sha256(digest.getDigestHex())
        var file = this.repo.download(context)
        val inputStreamResource = InputStreamResource(file.inputStream())
        val contentType = DockerSchemaUtils.getManifestType(path.projectId, path.repoName, manifestPath, this.repo)
        httpHeaders.set("Docker-Distribution-Api-Version", "registry/2.0")
        httpHeaders.set("Docker-Content-Digest", digest.toString())
        httpHeaders.set("Content-Type", contentType)
        logger.info("file result length {}， type {}", file.length(), contentType)
        return ResponseEntity.ok()
            .headers(httpHeaders)
            .contentLength(file.length())
            .body(inputStreamResource)
    }

    override fun deleteManifest(
        projectId: String,
        repoName: String,
        dockerRepo: String,
        reference: String
    ): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, projectId, repoName)
        try {
            return this.deleteManifestByDigest(
                DockerBasicPath(projectId, repoName, dockerRepo),
                DockerDigest(reference)
            )
        } catch (var4: Exception) {
            logger.error("unable to parse digest, delete manifest by tag '{}'", reference)
            return this.deleteManifestByTag(DockerBasicPath(projectId, repoName, dockerRepo), reference)
        }
    }

    private fun deleteManifestByDigest(path: DockerBasicPath, digest: DockerDigest): ResponseEntity<Any> {
        logger.info(
            "delete docker manifest for repo {} and digest {} in repo {}",
            path.dockerRepo,
            digest,
            path.repoName
        )
        val manifests = this.repo.findArtifacts(path.projectId, path.repoName, "manifest.json")
        val manifestIter = manifests.iterator()

        while (manifestIter.hasNext()) {
//            val manifest = manifestIter.next()
//            if (this.repo.canWrite(manifest.)) {
//                val manifestDigest = this.repo.getAttribute(projectId ,repoName ,manifest.path, digest.getDigestAlg())
//                if (StringUtils.isNotBlank(manifestDigest) && StringUtils.equals(manifestDigest, digest.getDigestHex()) && this.repo.delete(manifest.path)) {
//                    return ResponseEntity.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").build()
//                }
//            }
        }

        return DockerV2Errors.manifestUnknown(digest.toString())
    }

    private fun deleteManifestByTag(path: DockerBasicPath, tag: String): ResponseEntity<Any> {
        val tagPath = "${path.dockerRepo}/$tag"
        val manifestPath = "$tagPath/manifest.json"
        if (!this.repo.exists(path.projectId, path.repoName, manifestPath)) {
            return DockerV2Errors.manifestUnknown(manifestPath)
        } else if (this.repo.delete(tagPath)) {
            return ResponseEntity.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").build()
        } else {
            logger.error("unable to delete tag {}", manifestPath)
            return DockerV2Errors.manifestUnknown(manifestPath)
        }
    }

    override fun uploadManifest(
        path: DockerBasicPath,
        tag: String,
        mediaType: String,
        artifactFile: ArtifactFile
    ): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, path.projectId, path.repoName)
        if (!this.repo.canWrite(path)) {
            return DockerV2Errors.unauthorizedUpload()
        }
        val stream = artifactFile.getInputStream()
        logger.info(
            "deploy docker manifest for repo {} , tag {} into repo {} ,media type is  {}",
            path.dockerRepo,
            tag,
            path.repoName,
            mediaType
        )
        val manifestType = ManifestType.from(mediaType)
        val manifestPath = buildManifestPathFromType(path.dockerRepo, tag, manifestType)
        logger.info("upload manifest path {}", manifestPath)
        stream.use {
            val digest = this.processUploadedManifestType(
                path,
                tag,
                manifestPath,
                manifestType,
                it,
                artifactFile
            )
            this.repo.getWorkContextC().cleanup(path.repoName, "${path.dockerRepo}/_uploads")
            return ResponseEntity.status(201).header("Docker-Distribution-Api-Version", "registry/2.0")
                .header("Docker-Content-Digest", digest.toString()).build()
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
    private fun processUploadedManifestType(
        path: DockerBasicPath,
        tag: String,
        manifestPath: String,
        manifestType: ManifestType,
        stream: InputStream,
        artifactFile: ArtifactFile
    ): DockerDigest {
        val manifestBytes = IOUtils.toByteArray(stream)
        logger.info("manifest file content : {}", manifestBytes.toString())
        val digest = DockerManifestDigester.calc(manifestBytes)
        logger.info("manifest file digest : {}", digest)
        if (ManifestType.Schema2List == manifestType) {
            this.processManifestList(
                path,
                tag,
                manifestPath,
                digest!!,
                manifestBytes,
                manifestType
            )
            return digest
        } else {
            val manifestMetadata = ManifestDeserializer.deserialize(
                this.repo,
                path,
                tag,
                manifestType,
                manifestBytes,
                digest!!
            )
            this.addManifestsBlobs(manifestType, manifestBytes, manifestMetadata)
            if (!manifestSyncer.sync(this.repo, manifestMetadata, path, tag)) {
                val msg = "fail to  sync manifest blobs, canceling manifest upload"
                logger.error(msg)
                throw DockerSyncManifestException(msg)
            } else {
                logger.info("start to upload manifest : {}", manifestType.toString())
                val response = this.repo.upload(
                    this.manifestUploadContext(
                        path.projectId,
                        path.repoName,
                        manifestType,
                        manifestMetadata,
                        manifestPath,
                        manifestBytes,
                        artifactFile
                    )
                )
                if (!this.uploadSuccessful(response)) {
                    throw IOException(response.toString())
                } else {
                    val params = this.buildManifestPropertyMap(path.dockerRepo, tag, digest, manifestType)
                    val labels = manifestMetadata.tagInfo.labels
                    labels.entries().forEach {
                        params.set(it.key, it.value)
                    }
                    this.repo.setAttributes(path.projectId, path.repoName, manifestPath, params)
                    this.repo.getWorkContextC().onTagPushedSuccessfully(path.repoName, path.dockerRepo, tag)
                    return digest
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun processManifestList(
        path: DockerBasicPath,
        tag: String,
        manifestPath: String,
        digest: DockerDigest,
        manifestBytes: ByteArray,
        manifestType: ManifestType
    ) {
        val manifestList = ManifestListSchema2Deserializer.deserialize(manifestBytes)
        if (manifestList != null) {
            val iter = manifestList.manifests.iterator()
            // check every manifest in the repo
            while (iter.hasNext()) {
                val manifest = iter.next()
                val mDigest = manifest.digest
                val manifestFilename = DockerDigest(mDigest!!).filename()
                val manifestFile =
                    DockerUtils.findBlobGlobally(
                        this.repo,
                        path.projectId,
                        path.repoName,
                        path.dockerRepo,
                        manifestFilename
                    )
                if (manifestFile == null) {
                    throw DockerNotFoundException("manifest list (" + digest.toString() + ") miss manifest digest " + mDigest + ". ==>" + manifest.toString())
                }
            }
        }

        val response = this.repo.upload(
            this.manifestListUploadContext(
                path.projectId,
                path.repoName,
                manifestType,
                digest,
                manifestPath,
                manifestBytes
            )
        )
        if (this.uploadSuccessful(response)) {
            val params = this.buildManifestPropertyMap(path.dockerRepo, tag, digest, manifestType)
            this.repo.setAttributes(path.projectId, path.repoName, manifestPath, params)
            this.repo.getWorkContextC().onTagPushedSuccessfully(path.repoName, path.dockerRepo, tag)
        } else {
            throw IOException(response.toString())
        }
    }

    private fun manifestListUploadContext(
        projectId: String,
        repoName: String,
        manifestType: ManifestType,
        digest: DockerDigest,
        manifestPath: String,
        manifestBytes: ByteArray
    ): UploadContext {
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

    private fun buildManifestPropertyMap(
        dockerRepo: String,
        tag: String,
        digest: DockerDigest,
        manifestType: ManifestType
    ): HashMap<String, String> {
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
            logger.error("Error uploading manifest: '{}'", exception.message)
        }
    }

    @Throws(IOException::class)
    private fun addManifestsBlobs(
        manifestType: ManifestType,
        manifestBytes: ByteArray,
        manifestMetadata: ManifestMetadata
    ) {
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
                val configBytes = DockerSchemaUtils.fetchSchema2Manifest(
                    this.repo,
                    DockerUtils.getFullPath(manifestFile, this.repo.getWorkContextC())
                )
                this.addSchema2Blob(configBytes, manifestMetadata)
            }
        }
    }

    private fun manifestUploadContext(
        projectId: String,
        repoName: String,
        manifestType: ManifestType,
        manifestMetadata: ManifestMetadata,
        manifestPath: String,
        manifestBytes: ByteArray,
        artifactFile: ArtifactFile
    ): UploadContext {
        val context = UploadContext(projectId, repoName, manifestPath).content(ByteArrayInputStream(manifestBytes))
            .artifactFile(artifactFile)
        if ((manifestType.equals(ManifestType.Schema2) || manifestType.equals(ManifestType.Schema2List)) && "sha256" == manifestMetadata.tagInfo.digest?.getDigestAlg()) {
            context.sha256(manifestMetadata.tagInfo.digest!!.getDigestHex())
        }

        return context
    }

    override fun isBlobExists(
        projectId: String,
        repoName: String,
        dockerRepo: String,
        digest: DockerDigest
    ): ResponseEntity<Any> {
        try {
            RepoUtil.loadRepo(repo, userId, projectId, repoName)
            logger.info("is blob exist upload {}, {},{},{}", projectId, repoName, dockerRepo, digest.getDigestHex())
            if (DockerSchemaUtils.isEmptyBlob(digest)) {
                logger.info("request for empty layer for image {}", dockerRepo)
                return DockerSchemaUtils.emptyBlobHeadResponse()
            } else {
                val blob =
                    DockerUtils.getBlobFromRepoPath(this.repo, projectId, repoName, dockerRepo, digest.filename())
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
        } catch (e: PermissionCheckException) {
            logger.error("the user do not have permission to op")
            return DockerV2Errors.unauthorizedUpload()
        }
    }

    override fun getBlob(
        projectId: String,
        repoName: String,
        dockerRepo: String,
        digest: DockerDigest
    ): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, projectId, repoName)
        logger.info("fetch docker blob {} from repo {}", digest.getDigestHex(), repoName)
        if (DockerSchemaUtils.isEmptyBlob(digest)) {
            logger.info("request for empty layer for image {}", dockerRepo)
            return DockerSchemaUtils.emptyBlobGetResponse()
        } else {
            val blob = this.getRepoBlob(projectId, repoName, dockerRepo, digest)
            if (blob != null) {
                var context = DownloadContext(projectId, repoName, dockerRepo).projectId(projectId).repoName(repoName)
                    .sha256(digest.getDigestHex())
                var file = this.repo.download(context)
                httpHeaders.set("Docker-Distribution-Api-Version", "registry/2.0")
                httpHeaders.set("Docker-Content-Digest", digest.toString())
                val resource = InputStreamResource(file.inputStream())
                return ResponseEntity.ok()
                    .headers(httpHeaders)
                    .contentLength(file.length())
                    .contentType(MediaType.parseMediaType("application/octet-stream"))
                    .body(resource)
            } else {
                return DockerV2Errors.blobUnknown(digest.toString())
            }
        }
    }

    private fun getRepoBlob(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest): Artifact? {
        val result = this.repo.findArtifacts(projectId, repoName, digest.filename())
        if (result.size == 0) {
            return null
        }
        return Artifact(projectId, repoName, dockerRepo).sha256(digest.filename())
    }

    override fun startBlobUpload(
        projectId: String,
        repoName: String,
        dockerRepo: String,
        mount: String?
    ): ResponseEntity<Any> {
        try {
            RepoUtil.loadRepo(repo, userId, projectId, repoName)
            logger.info("start upload blob , {}, {}, {}", projectId, repoName, dockerRepo)
            if (!this.repo.canWrite(DockerBasicPath(projectId, repoName, dockerRepo))) {
                return DockerV2Errors.unauthorizedUpload()
            }
            val location: URI
            if (mount != null) {
                var mountDigest = DockerDigest(mount)
                val mountableBlob =
                    DockerUtils.findBlobGlobally(this.repo, projectId, repoName, dockerRepo, mountDigest.filename())
                if (mountableBlob != null) {
                    location = this.getDockerURI(repoName, "$dockerRepo/blobs/$mount")
                    logger.info(
                        "found accessible blob at {}/{} to mount  {}",
                        mountableBlob.repoName,
                        mountableBlob.path,
                        repoName + "/" + dockerRepo + "/" + mount
                    )
                    return ResponseEntity.status(201).header("Docker-Distribution-Api-Version", "registry/2.0")
                        .header("Docker-Content-Digest", mount).header("Content-Length", "0")
                        .header("Location", location.toString()).build()
                }
            }
            val uuid = this.repo.startAppend()
            location = this.getDockerURI(repoName, "$projectId/$repoName/$dockerRepo/blobs/uploads/$uuid")
            return ResponseEntity.status(202).header("Docker-Distribution-Api-Version", "registry/2.0")
                .header("Docker-Upload-Uuid", uuid).header("Location", location.toString()).build()
        } catch (e: PermissionCheckException) {
            return DockerV2Errors.unauthorizedUpload()
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
            logger.error("docker location url is blank, make sure the host request header exists.")
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
        if (protocolHeaders == null || protocolHeaders.isEmpty()) {
            return "http"
        }
        if (!protocolHeaders.isEmpty()) {
            return protocolHeaders.iterator().next() as String
        } else {
            logger.debug("X-Forwarded-Proto does not exist, return https.")
            return "https"
        }
    }

    override fun uploadBlob(
        projectId: String,
        repoName: String,
        dockerRepo: String,
        digest: DockerDigest,
        uuid: String,
        artifactFile: ArtifactFile
    ): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, projectId, repoName)
        return if (this.putHasStream()) this.uploadBlobFromPut(
            projectId,
            repoName,
            dockerRepo,
            digest,
            artifactFile
        ) else this.finishPatchUpload(projectId, repoName, dockerRepo, digest, uuid)
    }

    private fun putHasStream(): Boolean {
        val headerValues = httpHeaders.get("User-Agent")
        if (headerValues != null) {
            val headerIter = headerValues.iterator()

            while (headerIter.hasNext()) {
                val userAgent = headerIter.next() as String
                logger.info("User agent header: {}", userAgent)
                if (OLD_USER_AGENT_PATTERN.matcher(userAgent).matches()) {
                    return true
                }
            }
        }

        return false
    }

    private fun uploadBlobFromPut(
        projectId: String,
        repoName: String,
        dockerRepo: String,
        digest: DockerDigest,
        artifactFile: ArtifactFile
    ): ResponseEntity<Any> {
        val blobPath = dockerRepo + "/" + "_uploads" + "/" + digest.filename()
        if (!this.repo.canWrite(DockerBasicPath(projectId, repoName, dockerRepo))) {
            return this.consumeStreamAndReturnError(artifactFile.getInputStream())
        } else {
            logger.info("deploy docker blob {} into repo {}", blobPath, repoName)
            var context =
                UploadContext(projectId, repoName, blobPath).content(artifactFile.getInputStream()).projectId(projectId)
                    .repoName(repoName)
                    .sha256(digest.getDigestHex())
            val response = this.repo.upload(context)
            if (this.uploadSuccessful(response)) {
                val location = this.getDockerURI(repoName, "$dockerRepo/blobs/$digest")
                return ResponseEntity.created(location).header("Docker-Distribution-Api-Version", "registry/2.0")
                    .header("Docker-Content-Digest", digest.toString()).build()
            } else {
                logger.error(
                    "error upload blob {} status {} and message: {}",
                    blobPath,
                    response.statusCodeValue,
                    response.toString()
                )
                return DockerV2Errors.blobUploadInvalid(response.toString())
            }
        }
    }

    private fun finishPatchUpload(
        projectId: String,
        repoName: String,
        dockerRepo: String,
        digest: DockerDigest,
        uuid: String
    ): ResponseEntity<Any> {
        logger.info("finish upload blob {}", digest.getDigestHex())
        val fileName = digest.filename()
        val blobPath = "/$dockerRepo/_uploads/$fileName"
        var context = UploadContext(projectId, repoName, blobPath)
        this.repo.finishAppend(uuid, context)
        this.repo.getWorkContextC().setSystem()
        this.repo.getWorkContextC().unsetSystem()
        val location = this.getDockerURI(repoName, "$projectId/$repoName/$dockerRepo/blobs/$digest")
        return ResponseEntity.created(location).header("Docker-Distribution-Api-Version", "registry/2.0")
            .header("Content-Length", "0").header("Docker-Content-Digest", digest.toString()).build()
    }

    override fun patchUpload(
        projectId: String,
        repoName: String,
        dockerRepo: String,
        uuid: String,
        artifactFile: ArtifactFile
    ): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, projectId, repoName)
        logger.info("patch upload blob {}", uuid)
        // val blobPath = "$dockerRepo/_uploads/$uuid"
        val response = this.repo.writeAppend(uuid, artifactFile)
        val location = this.getDockerURI(repoName, "$projectId/$repoName/$dockerRepo/blobs/uploads/$uuid")
        return ResponseEntity.status(202).header("Content-Length", "0")
            .header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Upload-Uuid", uuid)
            .header("Location", location.toString()).header("Range", "0-" + (response - 1L)).build()
    }

    private fun consumeStreamAndReturnError(stream: InputStream): ResponseEntity<Any> {
        NullOutputStream().use {
            IOUtils.copy(stream, it)
        }
        return DockerV2Errors.unauthorizedUpload()
    }
}
