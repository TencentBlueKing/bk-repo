package com.tencent.bkrepo.docker.service

import com.tencent.bkrepo.common.api.util.toJsonString
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
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.response.CatalogResponse
import com.tencent.bkrepo.docker.response.DockerResponse
import com.tencent.bkrepo.docker.response.TagsResponse
import com.tencent.bkrepo.docker.util.ArtifactUtil
import com.tencent.bkrepo.docker.util.ContentUtil
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
    val contentUtil = ContentUtil(repo)
    val repoUtil = RepoUtil(repo)

    companion object {
        private val logger = LoggerFactory.getLogger(DockerV2LocalRepoService::class.java)
    }

    override fun ping(): DockerResponse {
        return ResponseEntity.ok().header("Content-Type", "application/json")
            .header("Docker-Distribution-Api-Version", "registry/2.0").body("{}")
    }

    override fun getTags(context: RequestContext, maxEntries: Int, lastEntry: String): DockerResponse {
        repoUtil.loadContext(context)
        val elementsHolder = DockerPaginationElementsHolder()
        val manifests = repo.getArtifactListByName(context.projectId, context.repoName, "manifest.json")

        if (manifests.isEmpty()) {
            return DockerV2Errors.nameUnknown(context.artifactName)
        }
        manifests.forEach {
            val path = it["path"] as String
            val tagName = path.apply {
                replaceAfterLast("/", "")
            }.apply {
                removeSuffix("/")
            }.apply {
                removePrefix("/" + context.artifactName + "/")
            }
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
            val last = tagsResponse.tags.last() as String
            val name = context.artifactName
            val link = "</v2/$name/tags/list?last=$last&n=$maxEntries>; rel=\"next\""
            httpHeaders.set("Link", link)
        }
        return ResponseEntity(tagsResponse, httpHeaders, HttpStatus.OK)
    }

    override fun catalog(context: RequestContext, maxEntries: Int, lastEntry: String): DockerResponse {
        repoUtil.loadContext(context)
        val manifests = repo.getArtifactListByName(context.projectId, context.repoName, "manifest.json")
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
            val last = catalogResponse.repositories.last() as String
            httpHeaders.set("Link", "</v2/_catalog?last=$last&n=$maxEntries>; rel=\"next\"")
        }
        return ResponseEntity(catalogResponse, httpHeaders, HttpStatus.OK)
    }

    override fun getManifest(context: RequestContext, reference: String): DockerResponse {
        repoUtil.loadContext(context)
        logger.info("get manifest params [$context,$reference]")
        return try {
            val digest = DockerDigest(reference)
            getManifestByDigest(context, digest)
        } catch (exception: Exception) {
            logger.warn("unable to parse digest, get manifest by tag [$reference]")
            getManifestByTag(context, reference)
        }
    }

    override fun getManifestString(context: RequestContext, tag: String): String {
        repoUtil.loadContext(context)
        val useManifestType = chooseManifestType(context, tag)
        val manifestPath = RepoServiceUtil.buildManifestPath(context.artifactName, tag, useManifestType)
        val manifest = repo.getArtifact(context.projectId, context.repoName, manifestPath) ?: run {
            logger.warn("node not exist [$context]")
            return EMPTYSTR
        }
        val downloadContext = DownloadContext(context).sha256(manifest.sha256!!).length(manifest.length)
        val inputStream = repo.download(downloadContext)
        return inputStream.readBytes().toString(Charset.defaultCharset())
    }

    override fun getRepoList(context: RequestContext): List<String> {
        repoUtil.loadContext(context)
        return repo.getDockerArtifactList(context.projectId, context.repoName)
    }

    override fun getRepoTagList(context: RequestContext): Map<String, String> {
        repoUtil.loadContext(context)
        return repo.getRepoTagList(context)
    }

    override fun buildLayerResponse(context: RequestContext, layerId: String): DockerResponse {
        repoUtil.loadContext(context)
        val digest = DockerDigest(layerId)
        val artifact = repo.getBlobListByDigest(context.projectId, context.repoName, digest.fileName()) ?: run {
            logger.warn("user [${context.userId}]  get artifact  [$this] fail: [$layerId] not found")
            throw DockerRepoNotFoundException(layerId)
        }
        logger.info("get blob info [$context, $artifact]")
        val length = artifact[0]["size"] as Int
        val downloadContext = DownloadContext(context).sha256(digest.getDigestHex()).length(length.toLong())
        val inputStreamResource = InputStreamResource(repo.download(downloadContext))
        with(context) {
            val contentType = contentUtil.getManifestType(projectId, repoName, artifactName)
            httpHeaders.apply {
                set("Docker-Distribution-Api-Version", "registry/2.0")
            }.apply {
                set("Docker-Distribution-Api-Version", "registry/2.0")
            }.apply {
                set("Content-Type", contentType)
            }
            return ResponseEntity.ok().headers(httpHeaders).contentLength(downloadContext.length).body(inputStreamResource)
        }
    }

    override fun deleteManifest(context: RequestContext, reference: String): DockerResponse {
        repoUtil.loadContext(context)
        return try {
            deleteManifestByDigest(context, DockerDigest(reference))
        } catch (exception: Exception) {
            logger.warn("unable to parse digest, delete manifest by tag [$context,$reference]")
            deleteManifestByTag(context, reference)
        }
    }

    private fun deleteManifestByDigest(context: RequestContext, digest: DockerDigest): DockerResponse {
        logger.info("delete docker manifest for  [$context}] digest [$digest] ")
        val manifests = repo.getArtifactListByName(context.projectId, context.repoName, "manifest.json")
        val manifestIter = manifests.iterator()

        while (manifestIter.hasNext()) {
            val manifest = manifestIter.next()
            val fullPath = manifest["fullPath"] as String
            if (!repo.canWrite(context)) {
                return DockerV2Errors.manifestUnknown(digest.toString())
            }
            with(context) {
                val manifestDigest = repo.getAttribute(projectId, repoName, fullPath, digest.getDigestAlg())
                manifestDigest.let {
                    if (StringUtils.equals(manifestDigest, digest.getDigestHex())) {
                        repo.delete(fullPath)
                    }
                }
            }
        }
        return ResponseEntity.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").build()
    }

    private fun deleteManifestByTag(pathContext: RequestContext, tag: String): DockerResponse {
        val tagPath = "${pathContext.artifactName}/$tag"
        val manifestPath = "$tagPath/manifest.json"
        if (!repo.exists(pathContext.projectId, pathContext.repoName, manifestPath)) {
            return DockerV2Errors.manifestUnknown(manifestPath)
        } else if (repo.delete(tagPath)) {
            return ResponseEntity.status(202).header("Docker-Distribution-Api-Version", "registry/2.0").build()
        }
            logger.warn("unable to delete tag [$manifestPath]")
        return DockerV2Errors.manifestUnknown(manifestPath)
    }

    override fun uploadManifest(
        context: RequestContext,
        tag: String,
        mediaType: String,
        file: ArtifactFile
    ): ResponseEntity<Any> {
        repoUtil.loadContext(context)
        if (!repo.canWrite(context)) {
            return DockerV2Errors.unauthorizedUpload()
        }
        val manifestType = ManifestType.from(mediaType)
        val manifestPath = RepoServiceUtil.buildManifestPath(context.artifactName, tag, manifestType)
        logger.info("upload manifest path [$context,$tag] ,media [$mediaType , manifestPath]")
        val digest = uploadManifestType(
            context, tag, manifestPath,
            manifestType, file
            )
        return ResponseEntity.status(201).apply {
            header("Docker-Distribution-Api-Version", "registry/2.0")
        }.apply {
            header("Docker-Content-Digest", digest.toString())
        }.build()
    }

    private fun uploadManifestType(
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
        }

        val metadata = ManifestDeserializer.deserialize(repo, context, tag, manifestType, manifestBytes, digest!!)
        contentUtil.addManifestsBlobs(context, manifestType, manifestBytes, metadata)
        if (!manifestSyncer.sync(metadata, context, tag)) {
            val msg = "fail to  sync manifest blobs, cancel manifest upload"
                logger.error(msg)
                throw DockerSyncManifestException(msg)
        }

        logger.info("start to upload manifest : [$manifestType]")
        with(context) {
                val uploadContext = RepoServiceUtil.manifestUploadContext(
                    projectId, repoName, manifestType,
                    metadata, manifestPath, artifactFile
                )
            if (!repo.upload(uploadContext)) {
                throw DockerFileSaveFailedException(manifestPath)
            }
            val params = RepoServiceUtil.buildManifestPropertyMap(artifactName, tag, digest, manifestType)
            val labels = metadata.tagInfo.labels
            labels.entries().forEach {
                params[it.key] = it.value
            }
            repo.setAttributes(projectId, repoName, manifestPath, params)
            return digest
        }
    }

    override fun isBlobExists(context: RequestContext, digest: DockerDigest): DockerResponse {
        repoUtil.loadContext(context)
        logger.debug("check blob exist [$context, $digest]")
        if (ContentUtil.isEmptyBlob(digest)) {
            logger.warn("request for empty layer for image [$context, $digest]")
            return ContentUtil.emptyBlobHeadResponse()
        }
        val blob = ArtifactUtil.getBlobFromRepo(repo, context, digest.fileName()) ?: run {
            return DockerV2Errors.blobUnknown(digest.toString())
        }
        return ResponseEntity.ok().apply {
            header("Docker-Distribution-Api-Version", "registry/2.0")
        }.apply {
            header("Docker-Content-Digest", digest.toString())
        }.apply {
            header("Content-Length", blob.length.toString())
        }.apply {
            header("Content-Type", "application/octet-stream")
        }.build<Any>()
    }

    override fun getBlob(context: RequestContext, digest: DockerDigest): ResponseEntity<Any> {
        repoUtil.loadContext(context)
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
        httpHeaders.apply {
            set("Docker-Distribution-Api-Version", "registry/2.0")
        }.apply {
            set("Docker-Content-Digest", digest.toString())
        }
        val resource = InputStreamResource(inputStream)
        return ResponseEntity.ok().apply {
            headers(httpHeaders)
        }.apply {
            contentLength(blob.length)
        }.apply {
            contentType(MediaType.parseMediaType("application/octet-stream"))
        }.body(resource)
    }

    override fun startBlobUpload(context: RequestContext, mount: String?): DockerResponse {
        repoUtil.loadContext(context)
        logger.info("start upload blob : [$context]")
        if (!repo.canWrite(context)) {
            return DockerV2Errors.unauthorizedUpload()
        }
        mount?.let {
            val mountDigest = DockerDigest(mount)
            val mountableBlob = ArtifactUtil.getBlobByName(repo, context, mountDigest.fileName())
            mountableBlob?.let {
                val location = RepoServiceUtil.getDockerURI("${context.artifactName}/blobs/$mount", httpHeaders)
                logger.info("found accessible blob at [$mountableBlob] to mount  [$context] [$mount]")
                return ResponseEntity.status(201).header("Docker-Distribution-Api-Version", "registry/2.0")
                    .header("Docker-Content-Digest", mount).header("Content-Length", "0")
                    .header("Location", location.toString()).build()
                }
        }
        val uuid = repo.startAppend()
        val startUrl = "${context.projectId}/${context.repoName}/${context.artifactName}/blobs/uploads/$uuid"
        val location = RepoServiceUtil.getDockerURI(startUrl, httpHeaders)
        return ResponseEntity.status(202).header("Docker-Distribution-Api-Version", "registry/2.0")
            .header("Docker-Upload-Uuid", uuid).header("Location", location.toString()).build()
    }

    override fun uploadBlob(
        context: RequestContext,
        digest: DockerDigest,
        uuid: String,
        file: ArtifactFile
    ): DockerResponse {
        repoUtil.loadContext(context)
        return if (RepoServiceUtil.putHasStream(httpHeaders)) {
            uploadBlobFromPut(context, digest, file)
        } else {
            finishPatchUpload(context, digest, uuid)
        }
    }

    override fun patchUpload(context: RequestContext, uuid: String, file: ArtifactFile): DockerResponse {
        with(context) {
            logger.debug("patch upload blob [$context, $uuid]")
            repoUtil.loadContext(context)
            val appendId = repo.writeAppend(uuid, file)
            val url = "$projectId/$repoName/$artifactName/blobs/uploads/$uuid"
            val location = RepoServiceUtil.getDockerURI(url, httpHeaders)
            return ResponseEntity.status(202).header("Content-Length", "0")
                .header("Docker-Distribution-Api-Version", "registry/2.0").header("Docker-Upload-Uuid", uuid)
                .header("Location", location.toString()).header("Range", "0-" + (appendId - 1L)).build()
        }
    }

    private fun uploadBlobFromPut(
        context: RequestContext,
        digest: DockerDigest,
        file: ArtifactFile
    ): DockerResponse {
        val blobPath = context.artifactName + "/" + "_uploads" + "/" + digest.fileName()
        if (!repo.canWrite(context)) {
            return RepoServiceUtil.consumeStreamAndReturnError(file.getInputStream())
        }
        logger.info("deploy docker blob [$blobPath] into [$context]")
        val uploadContext =
            UploadContext(context.projectId, context.repoName, blobPath).sha256(digest.getDigestHex())
                .artifactFile(file)
        val result = repo.upload(uploadContext)
        if (!result) {
            logger.warn("error upload blob [$blobPath]")
            return DockerV2Errors.blobUploadInvalid(context.artifactName)
        }
            val location = RepoServiceUtil.getDockerURI("${context.artifactName}/blobs/$digest", httpHeaders)
        return ResponseEntity.created(location).header("Docker-Distribution-Api-Version", "registry/2.0")
            .header("Docker-Content-Digest", digest.toString()).build()
    }

    private fun finishPatchUpload(context: RequestContext, digest: DockerDigest, uuid: String): DockerResponse {
        logger.debug("finish upload blob [$context, $digest,$uuid]")
        val fileName = digest.fileName()
        val blobPath = "/${context.artifactName}/_uploads/$fileName"
        val uploadContext = UploadContext(context.projectId, context.repoName, blobPath)
        repo.finishAppend(uuid, uploadContext)
        val url = "${context.projectId}/$context.repoName}/${context.artifactName}/blobs/$digest"
        val location = RepoServiceUtil.getDockerURI(url, httpHeaders)
        return ResponseEntity.created(location).header("Docker-Distribution-Api-Version", "registry/2.0")
            .header("Content-Length", "0").header("Docker-Content-Digest", digest.toString()).build()
    }

    private fun processManifestList(
        context: RequestContext,
        tag: String,
        manifestPath: String,
        digest: DockerDigest,
        manifestBytes: ByteArray,
        manifestType: ManifestType
    ) {
        val manifestList = ManifestListSchema2Deserializer.deserialize(manifestBytes)
        manifestList?.let {
            val iter = manifestList.manifests.iterator()
            // check every manifest in the repo
            while (iter.hasNext()) {
                val manifest = iter.next()
                val mDigest = manifest.digest
                val manifestFileName = DockerDigest(mDigest!!).fileName()
                ArtifactUtil.getBlobByName(repo, context, manifestFileName) ?: run {
                    throw DockerNotFoundException("manifest list [$digest] miss manifest digest $mDigest. ==>$manifest")
                }
            }
        }
        with(context) {
            val uploadContext = RepoServiceUtil.manifestListUploadContext(
                projectId, repoName, manifestType,
                digest, manifestPath, manifestBytes
            )

            if (!repo.upload(uploadContext)) {
                throw DockerFileSaveFailedException(manifestPath)
            }
            val params = RepoServiceUtil.buildManifestPropertyMap(context.artifactName, tag, digest, manifestType)
            repo.setAttributes(context.projectId, context.repoName, manifestPath, params)
        }
    }

    private fun chooseManifestType(context: RequestContext, tag: String): ManifestType {
        val acceptable = RepoServiceUtil.getAcceptableManifestTypes(httpHeaders)
        if (acceptable.contains(ManifestType.Schema2List)) {
            with(context) {
                val manifestPath = RepoServiceUtil.buildManifestPath(artifactName, tag, ManifestType.Schema2List)
                if (repo.exists(projectId, repoName, manifestPath)) {
                    return ManifestType.Schema2List
                }
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

    private fun getManifestByDigest(context: RequestContext, digest: DockerDigest): DockerResponse {
        logger.info("fetch docker manifest [$context}] and digest [$digest] ")
        var artifact = ArtifactUtil.getManifestByName(repo, context, "manifest.json")
        artifact?.let {
            val acceptable = RepoServiceUtil.getAcceptableManifestTypes(httpHeaders)
            if (acceptable.contains(ManifestType.Schema2List)) {
                artifact = ArtifactUtil.getManifestByName(repo, context, "list.manifest.json") ?: run {
                    return DockerV2Errors.manifestUnknown(digest.toString())
                }
            }
        }
        return contentUtil.buildManifestResponse(httpHeaders, context, context.artifactName, digest, artifact!!.length)
    }

    private fun getManifestByTag(context: RequestContext, tag: String): DockerResponse {
        val useManifestType = chooseManifestType(context, tag)
        val manifestPath = RepoServiceUtil.buildManifestPath(context.artifactName, tag, useManifestType)
        logger.info("get manifest by tag params [$context,$manifestPath]")
        if (!repo.canRead(context)) {
            logger.warn("do not have permission to get [$context,$manifestPath]")
            return DockerV2Errors.unauthorizedManifest(manifestPath, null as String?)
        }
        val manifest = repo.getArtifact(context.projectId, context.repoName, manifestPath) ?: run {
            logger.warn("the node not exist [$context,$manifestPath]")
            return DockerV2Errors.manifestUnknown(manifestPath)
        }
        logger.debug("get manifest by tag result [$manifest]")
        val digest = DockerDigest("sh256:${manifest.sha256}")
        return contentUtil.buildManifestResponse(httpHeaders, context, manifestPath, digest, manifest.length)
    }
}
