package com.tencent.bkrepo.docker.service

import com.fasterxml.jackson.databind.JsonNode
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.docker.artifact.DockerArtifactRepo
import com.tencent.bkrepo.docker.constant.EMPTYSTR
import com.tencent.bkrepo.docker.context.DownloadContext
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.errors.DockerV2Errors
import com.tencent.bkrepo.docker.exception.DockerFileSaveFailedException
import com.tencent.bkrepo.docker.exception.DockerNotFoundException
import com.tencent.bkrepo.docker.exception.DockerRepoNotFoundException
import com.tencent.bkrepo.docker.exception.DockerSyncManifestException
import com.tencent.bkrepo.docker.helpers.DockerCatalogTagsSlicer
import com.tencent.bkrepo.docker.helpers.DockerManifestDigester
import com.tencent.bkrepo.docker.helpers.DockerManifestSyncer
import com.tencent.bkrepo.docker.helpers.DockerPaginationElementsHolder
import com.tencent.bkrepo.docker.manifest.ManifestDeserializer
import com.tencent.bkrepo.docker.manifest.ManifestListSchema2Deserializer
import com.tencent.bkrepo.docker.manifest.ManifestType
import com.tencent.bkrepo.docker.model.DockerBlobInfo
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata
import com.tencent.bkrepo.docker.response.CatalogResponse
import com.tencent.bkrepo.docker.response.TagsResponse
import com.tencent.bkrepo.docker.util.ArtifactUtil
import com.tencent.bkrepo.docker.util.ContentUtil
import com.tencent.bkrepo.docker.util.JsonUtil
import com.tencent.bkrepo.docker.util.RepoServiceUtil
import com.tencent.bkrepo.docker.util.RepoUtil
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.nio.charset.Charset

@Service
class DockerV2LocalRepoService @Autowired constructor(val repo: DockerArtifactRepo) : DockerV2RepoService {

    var httpHeaders: HttpHeaders = HttpHeaders()
    val manifestSyncer = DockerManifestSyncer(repo)
    lateinit var userId: String

    companion object {
        private val logger = LoggerFactory.getLogger(DockerV2LocalRepoService::class.java)
    }

    override fun ping(): ResponseEntity<Any> {
        return ResponseEntity.ok().header("Content-Type", "application/json")
            .header("Docker-Distribution-Api-Version", "registry/2.0").body("{}")
    }

    override fun getTags(context: RequestContext, maxEntries: Int, lastEntry: String): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, context.projectId, context.repoName)
        val elementsHolder = DockerPaginationElementsHolder()
        val manifests = repo.getArtifactListByName(context.projectId, context.repoName, "manifest.json")

        if (manifests.isNotEmpty()) {
            manifests.forEach {
                val path = it["path"] as String
                val tagName =
                    path.replaceAfterLast("/", "").removeSuffix("/").removePrefix("/" + context.artifactName + "/")
                elementsHolder.elements.add(tagName)
            }

            if (elementsHolder.elements.isEmpty()) {
                return DockerV2Errors.nameUnknown(context.artifactName)
            }
            DockerCatalogTagsSlicer.sliceCatalog(elementsHolder, maxEntries, lastEntry)
            val shouldAddLinkHeader = elementsHolder.hasMoreElements
            val tagsResponse = TagsResponse(elementsHolder, context.artifactName)
            httpHeaders.set("Docker-Distribution-Api-Version", "registry/2.0")
            if (shouldAddLinkHeader) {
                httpHeaders.set(
                    "Link",
                    "</v2/" + context.artifactName + "/tags/list?last=" + tagsResponse.tags.last() as String + "&n=" + maxEntries + ">; rel=\"next\""
                )
            }
            return ResponseEntity(tagsResponse, httpHeaders, HttpStatus.OK)
        } else {
            return DockerV2Errors.nameUnknown(context.artifactName)
        }
    }

    override fun catalog(projectId: String, name: String, maxEntries: Int, lastEntry: String): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, projectId, name)
        val manifests = repo.getArtifactListByName(projectId, name, "manifest.json")
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

    override fun getManifest(context: RequestContext, reference: String): ResponseEntity<Any> {
        logger.info("get manifest params [$context,$reference]")
        RepoUtil.loadRepo(repo, userId, context.projectId, context.repoName)
        return try {
            val digest = DockerDigest(reference)
            getManifestByDigest(context, digest)
        } catch (exception: Exception) {
            logger.warn("unable to parse digest, fetching manifest by tag [$reference]")
            getManifestByTag(context, reference)
        }
    }

    private fun getManifestByDigest(pathContext: RequestContext, digest: DockerDigest): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, pathContext.projectId, pathContext.repoName)
        logger.info("fetch docker manifest [$pathContext}] and digest [$digest] ")

        var artifact = ArtifactUtil.getManifestByName(repo, pathContext, "manifest.json")
        if (artifact == null) {
            val acceptable = RepoServiceUtil.getAcceptableManifestTypes(httpHeaders)
            if (acceptable.contains(ManifestType.Schema2List)) {
                artifact = ArtifactUtil.getManifestByName(repo, pathContext, "list.manifest.json") ?: run {
                    return DockerV2Errors.manifestUnknown(digest.toString())
                }
            }
        }

        return buildManifestResponse(pathContext, pathContext.artifactName, digest, artifact!!.length)
    }

    private fun getManifestByTag(pathContext: RequestContext, tag: String): ResponseEntity<Any> {
        val useManifestType = chooseManifestType(pathContext, tag)
        val manifestPath = RepoServiceUtil.buildManifestPathFromType(pathContext.artifactName, tag, useManifestType)
        logger.info("get manifest by tag params [$pathContext,$manifestPath]")
        if (!repo.canRead(pathContext)) {
            return DockerV2Errors.unauthorizedManifest(manifestPath, null as String?)
        } else if (!repo.exists(pathContext.projectId, pathContext.repoName, manifestPath)) {
            return DockerV2Errors.manifestUnknown(manifestPath)
        } else {
            val manifest = repo.getArtifact(pathContext.projectId, pathContext.repoName, manifestPath) ?: run {
                return DockerV2Errors.manifestUnknown(manifestPath)
            }
            logger.debug("get manifest by tag result [$manifest]")
            val digest = DockerDigest("sh256:${manifest.sha256}")
            return buildManifestResponse(pathContext, manifestPath, digest, manifest.length)
        }
    }

    private fun chooseManifestType(pathContext: RequestContext, tag: String): ManifestType {
        val acceptable = RepoServiceUtil.getAcceptableManifestTypes(httpHeaders)
        if (acceptable.contains(ManifestType.Schema2List)) {
            val manifestPath =
                RepoServiceUtil.buildManifestPathFromType(pathContext.artifactName, tag, ManifestType.Schema2List)
            if (repo.exists(pathContext.projectId, pathContext.repoName, manifestPath)) {
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

    fun getManifestString(pathContext: RequestContext, tag: String): String {
        RepoUtil.loadRepo(repo, userId, pathContext.projectId, pathContext.repoName)
        val useManifestType = chooseManifestType(pathContext, tag)
        val manifestPath = RepoServiceUtil.buildManifestPathFromType(pathContext.artifactName, tag, useManifestType)
        val manifest = repo.getArtifact(pathContext.projectId, pathContext.repoName, manifestPath) ?: run {
            logger.warn("node not exist [$pathContext]")
            return EMPTYSTR
        }
        val context = DownloadContext(pathContext).sha256(manifest.sha256!!).length(manifest.length)
        val inputStream = repo.download(context)
        return inputStream.readBytes().toString(Charset.defaultCharset())
    }

    fun getRepoList(projectId: String, repoName: String): List<String> {
        RepoUtil.loadRepo(repo, userId, projectId, repoName)
        return repo.getDockerArtifactList(projectId, repoName)
    }

    fun getRepoTagList(projectId: String, repoName: String, image: String): Map<String, String> {
        return repo.getRepoTagList(projectId, repoName, image)
    }

    fun buildLayerResponse(pathContext: RequestContext, id: String): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, pathContext.projectId, pathContext.repoName)
        val digest = DockerDigest(id)
        val artifact = repo.getBlobListByDigest(pathContext.projectId, pathContext.repoName, digest.fileName())
        if (artifact.isEmpty()) {
            logger.warn("user [$userId]  get artifact  [$pathContext] fail: [$id] not found")
            throw DockerRepoNotFoundException(id)
        }
        logger.info("get blob info [$artifact]")
        val length = artifact[0]["size"] as Int
        val context = DownloadContext(pathContext).sha256(digest.getDigestHex()).length(length.toLong())
        val inputStreamResource = InputStreamResource(repo.download(context))
        val contentType = ContentUtil.getManifestType(
            repo,
            pathContext.projectId,
            pathContext.repoName,
            pathContext.artifactName
        )
        httpHeaders.set("Docker-Distribution-Api-Version", "registry/2.0")
        httpHeaders.set("Docker-Content-Digest", digest.toString())
        httpHeaders.set("Content-Type", contentType)
        return ResponseEntity.ok().headers(httpHeaders).contentLength(context.length).body(inputStreamResource)
    }

    private fun buildManifestResponse(
        pathContext: RequestContext,
        manifestPath: String,
        digest: DockerDigest,
        length: Long
    ): ResponseEntity<Any> {
        val context = DownloadContext(pathContext).length(length).sha256(digest.getDigestHex())
        val inputStream = repo.download(context)
        val inputStreamResource = InputStreamResource(inputStream)
        val contentType = ContentUtil.getManifestType(repo, pathContext.projectId, pathContext.repoName, manifestPath)
        httpHeaders.set("Docker-Distribution-Api-Version", "registry/2.0")
        httpHeaders.set("Docker-Content-Digest", digest.toString())
        httpHeaders.set("Content-Type", contentType)
        logger.info("file [$digest] result length [$length] type [$contentType]")
        return ResponseEntity.ok()
            .headers(httpHeaders)
            .contentLength(length)
            .body(inputStreamResource)
    }

    override fun deleteManifest(context: RequestContext, reference: String): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, context.projectId, context.repoName)
        return try {
            deleteManifestByDigest(context, DockerDigest(reference))
        } catch (exception: Exception) {
            logger.warn("unable to parse digest, delete manifest by tag [$context,$reference]")
            deleteManifestByTag(context, reference)
        }
    }

    private fun deleteManifestByDigest(pathContext: RequestContext, digest: DockerDigest): ResponseEntity<Any> {
        logger.info("delete docker manifest for  [${pathContext.artifactName}] digest [$digest] in repo [${pathContext.repoName}]")
        val manifests = repo.getArtifactListByName(pathContext.projectId, pathContext.repoName, "manifest.json")
        val manifestIter = manifests.iterator()

        while (manifestIter.hasNext()) {
//            val manifest = manifestIter.next()
//            if (repo.canWrite(manifest.)) {
//                val manifestDigest = repo.getAttribute(projectId ,repoName ,manifest.path, digest.getDigestAlg())
//                if (StringUtils.isNotBlank(manifestDigest) && StringUtils.equals(manifestDigest, digest.getDigestHex()) && repo.delete(manifest.path)) {
//                    return ResponseEntity.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").build()
//                }
//            }
        }

        return DockerV2Errors.manifestUnknown(digest.toString())
    }

    private fun deleteManifestByTag(pathContext: RequestContext, tag: String): ResponseEntity<Any> {
        val tagPath = "${pathContext.artifactName}/$tag"
        val manifestPath = "$tagPath/manifest.json"
        return if (!repo.exists(pathContext.projectId, pathContext.repoName, manifestPath)) {
            DockerV2Errors.manifestUnknown(manifestPath)
        } else if (repo.delete(tagPath)) {
            ResponseEntity.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").build()
        } else {
            logger.warn("unable to delete tag [$manifestPath]")
            DockerV2Errors.manifestUnknown(manifestPath)
        }
    }

    override fun uploadManifest(
        context: RequestContext,
        tag: String,
        mediaType: String,
        file: ArtifactFile
    ): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, context.projectId, context.repoName)
        if (!repo.canWrite(context)) {
            return DockerV2Errors.unauthorizedUpload()
        }
        val manifestType = ManifestType.from(mediaType)
        val manifestPath = RepoServiceUtil.buildManifestPathFromType(context.artifactName, tag, manifestType)
        logger.info("upload manifest path [$context,$tag] ,media [$mediaType]， [$manifestPath]")
        val digest = uploadedManifestType(
                context,
                tag,
                manifestPath,
                manifestType,
                file
            )
        return ResponseEntity.status(201).header("Docker-Distribution-Api-Version", "registry/2.0")
                .header("Docker-Content-Digest", digest.toString()).build()
    }

    private fun uploadedManifestType(
        context: RequestContext,
        tag: String,
        manifestPath: String,
        manifestType: ManifestType,
        artifactFile: ArtifactFile
    ): DockerDigest {
        val manifestBytes = IOUtils.toByteArray(artifactFile.getInputStream())
        val digest = DockerManifestDigester.calc(manifestBytes)
        logger.info("manifest file digest content digest : [$digest] ")
        if (ManifestType.Schema2List == manifestType) {
            processManifestList(context, tag, manifestPath, digest!!, manifestBytes, manifestType)
            return digest
        } else {
            val manifestMetadata =
                ManifestDeserializer.deserialize(repo, context, tag, manifestType, manifestBytes, digest!!)
            addManifestsBlobs(context, manifestType, manifestBytes, manifestMetadata)
            if (!manifestSyncer.sync(manifestMetadata, context, tag)) {
                val msg = "fail to  sync manifest blobs, canceling manifest upload"
                logger.error(msg)
                throw DockerSyncManifestException(msg)
            } else {
                logger.info("start to upload manifest : [$manifestType]")
                val uploadContext = RepoServiceUtil.manifestUploadContext(
                    context.projectId,
                    context.repoName,
                    manifestType,
                    manifestMetadata,
                    manifestPath,
                    artifactFile
                )
                if (repo.upload(uploadContext)) {
                    val params =
                        RepoServiceUtil.buildManifestPropertyMap(context.artifactName, tag, digest, manifestType)
                    val labels = manifestMetadata.tagInfo.labels
                    labels.entries().forEach {
                        params[it.key] = it.value
                    }
                    repo.setAttributes(context.projectId, context.repoName, manifestPath, params)
                    return digest
                } else {
                    throw DockerFileSaveFailedException(manifestPath)
                }
            }
        }
    }

    private fun processManifestList(
        pathContext: RequestContext,
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
                val manifestFileName = DockerDigest(mDigest!!).fileName()
                ArtifactUtil.getBlobByName(repo, pathContext, manifestFileName) ?: run {
                    throw DockerNotFoundException("manifest list [$digest] miss manifest digest $mDigest. ==>$manifest")
                }
            }
        }

        val context = RepoServiceUtil.manifestListUploadContext(
            pathContext.projectId,
            pathContext.repoName,
            manifestType,
            digest,
            manifestPath,
            manifestBytes
        )
        if (repo.upload(context)) {
            val params = RepoServiceUtil.buildManifestPropertyMap(pathContext.artifactName, tag, digest, manifestType)
            repo.setAttributes(pathContext.projectId, pathContext.repoName, manifestPath, params)
        } else {
            throw DockerFileSaveFailedException(manifestPath)
        }
    }

    private fun addManifestsBlobs(
        context: RequestContext,
        type: ManifestType,
        bytes: ByteArray,
        metadata: ManifestMetadata
    ) {
        if (ManifestType.Schema2 == type) {
            addSchema2Blob(bytes, metadata)
        } else if (ManifestType.Schema2List == type) {
            addSchema2ListBlobs(context, bytes, metadata)
        }
    }

    private fun addSchema2Blob(bytes: ByteArray, metadata: ManifestMetadata) {
        val manifest = JsonUtil.readTree(bytes)
        val config = manifest.get("config")
        if (config != null) {
            val digest = config.get("digest").asText()
            val blobInfo = DockerBlobInfo("", digest, 0L, "")
            metadata.blobsInfo.add(blobInfo)
        }
    }

    private fun addSchema2ListBlobs(context: RequestContext, bytes: ByteArray, metadata: ManifestMetadata) {
        val manifestList = JsonUtil.readTree(bytes)
        val manifests = manifestList.get("manifests")
        val manifest = manifests.iterator()

        while (manifest.hasNext()) {
            val manifestNode = manifest.next() as JsonNode
            val digestString = manifestNode.get("platform").get("digest").asText()
            val dockerBlobInfo = DockerBlobInfo("", digestString, 0L, "")
            metadata.blobsInfo.add(dockerBlobInfo)
            val manifestFileName = DockerDigest(digestString).fileName()
            val manifestFile = ArtifactUtil.getManifestByName(repo, context, manifestFileName)
            if (manifestFile != null) {
                val fullPath = ArtifactUtil.getFullPath(manifestFile)
                val configBytes = ContentUtil.getSchema2ManifestContent(repo, context, fullPath)
                addSchema2Blob(configBytes, metadata)
            }
        }
    }

    override fun isBlobExists(context: RequestContext, digest: DockerDigest): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, context.projectId, context.repoName)
        logger.info("check blob exist [$context, $digest]")
        if (ContentUtil.isEmptyBlob(digest)) {
            logger.warn("request for empty layer for image [$context, $digest]")
            return ContentUtil.emptyBlobHeadResponse()
        }
        val blob = ArtifactUtil.getBlobFromRepo(repo, context, digest.fileName()) ?: run {
            return DockerV2Errors.blobUnknown(digest.toString())
        }
        return ResponseEntity.ok().header("Docker-Distribution-Api-Version", "registry/2.0")
            .header("Docker-Content-Digest", digest.toString())
            .header("Content-Length", blob.length.toString())
            .header("Content-Type", "application/octet-stream").build<Any>()
    }

    override fun getBlob(context: RequestContext, digest: DockerDigest): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, context.projectId, context.repoName)
        if (ContentUtil.isEmptyBlob(digest)) {
            logger.warn("get empty layer for image [$context, $digest]")
            return ContentUtil.emptyBlobGetResponse()
        }
        val blob = ArtifactUtil.getBlobByName(repo, context, digest.fileName()) ?: run {
            logger.warn("get blob globally [$context,$digest] empty")
            return DockerV2Errors.blobUnknown(digest.toString())
        }
        logger.info("get blob [$digest] from repo [${context.artifactName}] ,length [${blob.length}]")
        val downloadContext = DownloadContext(context).sha256(digest.getDigestHex()).length(blob.length)
            val inputStream = repo.download(downloadContext)
                httpHeaders.set("Docker-Distribution-Api-Version", "registry/2.0")
                httpHeaders.set("Docker-Content-Digest", digest.toString())
                val resource = InputStreamResource(inputStream)
        return ResponseEntity.ok()
            .headers(httpHeaders)
            .contentLength(blob.length)
            .contentType(MediaType.parseMediaType("application/octet-stream"))
            .body(resource)
    }

    override fun startBlobUpload(context: RequestContext, mount: String?): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, context.projectId, context.repoName)
        logger.info("start upload blob : [$context]")
        if (!repo.canWrite(context)) {
            return DockerV2Errors.unauthorizedUpload()
        }
        if (mount != null) {
            var mountDigest = DockerDigest(mount)
            val mountableBlob = ArtifactUtil.getBlobByName(repo, context, mountDigest.fileName())
            if (mountableBlob != null) {
                val location = RepoServiceUtil.getDockerURI("${context.artifactName}/blobs/$mount", httpHeaders)
                logger.info("found accessible blob at [$mountableBlob] to mount  [$context] [$mount]")
                return ResponseEntity.status(201).header("Docker-Distribution-Api-Version", "registry/2.0")
                        .header("Docker-Content-Digest", mount).header("Content-Length", "0")
                        .header("Location", location.toString()).build()
                }
        }
        val uuid = repo.startAppend()
        val location = RepoServiceUtil.getDockerURI(
            "${context.projectId}/${context.repoName}/${context.artifactName}/blobs/uploads/$uuid",
                httpHeaders
        )
        return ResponseEntity.status(202).header("Docker-Distribution-Api-Version", "registry/2.0")
            .header("Docker-Upload-Uuid", uuid).header("Location", location.toString()).build()
    }

    override fun uploadBlob(
        context: RequestContext,
        digest: DockerDigest,
        uuid: String,
        file: ArtifactFile
    ): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, context.projectId, context.repoName)
        return if (RepoServiceUtil.putHasStream(httpHeaders)) {
            uploadBlobFromPut(context, digest, file)
        } else {
            finishPatchUpload(context, digest, uuid)
        }
    }

    private fun uploadBlobFromPut(
        context: RequestContext,
        digest: DockerDigest,
        file: ArtifactFile
    ): ResponseEntity<Any> {
        val blobPath = context.artifactName + "/" + "_uploads" + "/" + digest.fileName()
        if (!repo.canWrite(context)) {
            return RepoServiceUtil.consumeStreamAndReturnError(file.getInputStream())
        }
        logger.info("deploy docker blob [$blobPath] into [$context]")
        val uploadContext =
                UploadContext(
                    context.projectId,
                    context.repoName,
                    blobPath
                ).sha256(digest.getDigestHex()).artifactFile(file)
        val result = repo.upload(uploadContext)
        return if (result) {
            val location = RepoServiceUtil.getDockerURI("${context.artifactName}/blobs/$digest", httpHeaders)
                ResponseEntity.created(location).header("Docker-Distribution-Api-Version", "registry/2.0")
                    .header("Docker-Content-Digest", digest.toString()).build()
            } else {
            logger.error("error upload blob [$blobPath]")
            DockerV2Errors.blobUploadInvalid(context.artifactName)
        }
    }

    private fun finishPatchUpload(context: RequestContext, digest: DockerDigest, uuid: String): ResponseEntity<Any> {
        logger.info("finish upload blob [$digest]")
        val fileName = digest.fileName()
        val blobPath = "/${context.artifactName}/_uploads/$fileName"
        var uploadContext = UploadContext(context.projectId, context.repoName, blobPath)
        repo.finishAppend(uuid, uploadContext)
        val url = "${context.projectId}/${context.repoName}/${context.artifactName}/blobs/$digest"
        val location = RepoServiceUtil.getDockerURI(url, httpHeaders)
        return ResponseEntity.created(location).header("Docker-Distribution-Api-Version", "registry/2.0")
            .header("Content-Length", "0").header("Docker-Content-Digest", digest.toString()).build()
    }

    override fun patchUpload(context: RequestContext, uuid: String, file: ArtifactFile): ResponseEntity<Any> {
        RepoUtil.loadRepo(repo, userId, context.projectId, context.repoName)
        logger.info("patch upload blob [$uuid]")
        val appendId = repo.writeAppend(uuid, file)
        val url = "${context.projectId}/${context.repoName}/${context.artifactName}/blobs/uploads/$uuid"
        val location = RepoServiceUtil.getDockerURI(url, httpHeaders)
        return ResponseEntity.status(202).header("Content-Length", "0")
            .header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Upload-Uuid", uuid)
            .header("Location", location.toString()).header("Range", "0-" + (appendId - 1L)).build()
    }
}
