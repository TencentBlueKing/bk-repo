package com.tencent.bkrepo.docker.service

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.docker.artifact.DockerArtifactRepo
import com.tencent.bkrepo.docker.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_CONTENT_DIGEST
import com.tencent.bkrepo.docker.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_LIST
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_FULL_PATH
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_PATH
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_SIZE
import com.tencent.bkrepo.docker.constant.DOCKER_PRE_SUFFIX
import com.tencent.bkrepo.docker.constant.DOCKER_UPLOAD_UUID
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
import org.springframework.http.HttpHeaders.CONTENT_LENGTH
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.nio.charset.Charset

/**
 * docker v2 protocol to work with
 * local storage
 * @author: owenlxu
 * @date: 2019-10-15
 */
@Service
class DockerV2LocalRepoService @Autowired constructor(val repo: DockerArtifactRepo) : DockerV2RepoService {

    var httpHeaders: HttpHeaders = HttpHeaders()
    val contentUtil = ContentUtil(repo)
    val repoUtil = RepoUtil(repo)

    companion object {
        private val logger = LoggerFactory.getLogger(DockerV2LocalRepoService::class.java)
    }

    override fun ping(): DockerResponse {
        return ResponseEntity.ok().apply {
            header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        }.apply {
            header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
        }.body("{}")
    }

    override fun getTags(context: RequestContext, maxEntries: Int, lastEntry: String): DockerResponse {
        repoUtil.loadContext(context)
        val elementsHolder = DockerPaginationElementsHolder()
        val manifests = repo.getArtifactListByName(context.projectId, context.repoName, DOCKER_MANIFEST)

        if (manifests.isEmpty()) {
            return DockerV2Errors.nameUnknown(context.artifactName)
        }
        manifests.forEach {
            val path = it[DOCKER_NODE_PATH] as String
            val tagName = path.apply {
                replaceAfterLast(DOCKER_PRE_SUFFIX, EMPTYSTR)
            }.apply {
                removeSuffix(DOCKER_PRE_SUFFIX)
            }.apply {
                removePrefix(DOCKER_PRE_SUFFIX + context.artifactName + DOCKER_PRE_SUFFIX)
            }
            elementsHolder.elements.add(tagName)
        }

        if (elementsHolder.elements.isEmpty()) {
            return DockerV2Errors.nameUnknown(context.artifactName)
        }
        DockerCatalogTagsSlicer.sliceCatalog(elementsHolder, maxEntries, lastEntry)
        val shouldAddLinkHeader = elementsHolder.hasMoreElements
        val tagsResponse = TagsResponse(elementsHolder, context.artifactName)
        httpHeaders.set(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
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
        val manifests = repo.getArtifactListByName(context.projectId, context.repoName, DOCKER_MANIFEST)
        val elementsHolder = DockerPaginationElementsHolder()

        manifests.forEach {
            val path = it[DOCKER_NODE_PATH] as String
            val repoName =
                path.replaceAfterLast(DOCKER_PRE_SUFFIX, EMPTYSTR).replaceAfterLast(DOCKER_PRE_SUFFIX, EMPTYSTR)
                    .removeSuffix(DOCKER_PRE_SUFFIX)
            if (StringUtils.isNotBlank(repoName)) {
                elementsHolder.addElement(repoName)
            }
            DockerCatalogTagsSlicer.sliceCatalog(elementsHolder, maxEntries, lastEntry)
        }
        val shouldAddLinkHeader = elementsHolder.hasMoreElements
        val catalogResponse = CatalogResponse(elementsHolder)
        httpHeaders.set(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
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
        } catch (exception: IllegalArgumentException) {
            logger.warn("unable to parse digest, get manifest by tag [$context,$reference]")
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
        val length = artifact[0][DOCKER_NODE_SIZE] as Int
        val downloadContext = DownloadContext(context).sha256(digest.getDigestHex()).length(length.toLong())
        val inputStreamResource = InputStreamResource(repo.download(downloadContext))
        with(context) {
            val contentType = contentUtil.getManifestType(projectId, repoName, artifactName)
            httpHeaders.apply {
                set(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
            }.apply {
                set(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
            }.apply {
                set(CONTENT_TYPE, contentType)
            }
            return ResponseEntity.ok().headers(httpHeaders).contentLength(downloadContext.length).body(inputStreamResource)
        }
    }

    override fun deleteManifest(context: RequestContext, reference: String): DockerResponse {
        repoUtil.loadContext(context)
        return try {
            deleteManifestByDigest(context, DockerDigest(reference))
        } catch (exception: IllegalArgumentException) {
            logger.warn("unable to parse digest, delete manifest by tag [$context,$reference]")
            deleteManifestByTag(context, reference)
        }
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
        val digest = uploadManifestType(context, tag, manifestPath, manifestType, file)
        return ResponseEntity.status(HttpStatus.CREATED).apply {
            header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
        }.apply {
            header(DOCKER_CONTENT_DIGEST, digest.toString())
        }.build()
    }

    // delete a manifest file by tag first
    private fun deleteManifestByTag(pathContext: RequestContext, tag: String): DockerResponse {
        val tagPath = "${pathContext.artifactName}/$tag"
        val manifestPath = "$tagPath/$DOCKER_MANIFEST"
        if (!repo.exists(pathContext.projectId, pathContext.repoName, manifestPath)) {
            return DockerV2Errors.manifestUnknown(manifestPath)
        } else if (repo.delete(tagPath)) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                .build()
        }
        logger.warn("unable to delete tag [$manifestPath]")
        return DockerV2Errors.manifestUnknown(manifestPath)
    }

    // delete a manifest file by digest then
    private fun deleteManifestByDigest(context: RequestContext, digest: DockerDigest): DockerResponse {
        logger.info("delete docker manifest for  [$context}] digest [$digest] ")
        val manifests = repo.getArtifactListByName(context.projectId, context.repoName, DOCKER_MANIFEST)
        val manifestIter = manifests.iterator()

        while (manifestIter.hasNext()) {
            val manifest = manifestIter.next()
            val fullPath = manifest[DOCKER_NODE_FULL_PATH] as String
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
        return ResponseEntity.status(HttpStatus.ACCEPTED).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION).build()
    }

    // upload a manifest file
    private fun uploadManifestType(context: RequestContext, tag: String, manifestPath: String, manifestType: ManifestType, artifactFile: ArtifactFile): DockerDigest {
        val manifestBytes = IOUtils.toByteArray(artifactFile.getInputStream())
        val digest = DockerManifestDigester.calc(manifestBytes)
        logger.info("manifest file digest content digest : [$digest] ")
        if (ManifestType.Schema2List == manifestType) {
            processManifestList(context, tag, manifestPath, digest!!, manifestBytes)
            return digest
        }

        val metadata = ManifestDeserializer.deserialize(repo, context, tag, manifestType, manifestBytes, digest!!)
        contentUtil.addManifestsBlobs(context, manifestType, manifestBytes, metadata)
        if (!DockerManifestSyncer.sync(repo, metadata, context, tag)) {
            val msg = "fail to  sync manifest blobs, cancel manifest upload"
            logger.error(msg)
            throw DockerSyncManifestException(msg)
        }

        logger.info("start to upload manifest : [$manifestType]")
        with(context) {
            val uploadContext = RepoServiceUtil.manifestUploadContext(
                context, manifestType,
                metadata, manifestPath, artifactFile
            )
            val params = RepoServiceUtil.buildManifestPropertyMap(artifactName, tag, digest, manifestType)
            val labels = metadata.tagInfo.labels
            labels.entries().forEach {
                params[it.key] = it.value
            }
            uploadContext.metadata(params)
            if (!repo.upload(uploadContext)) {
                throw DockerFileSaveFailedException(manifestPath)
            }
            return digest
        }
    }

    // check is a blob file exist in this repo
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
            header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
        }.apply {
            header(DOCKER_CONTENT_DIGEST, digest.toString())
        }.apply {
            header(CONTENT_LENGTH, blob.length.toString())
        }.apply {
            header(CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
        }.build<Any>()
    }

    // get a blob file
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
            set(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
        }.apply {
            set(DOCKER_CONTENT_DIGEST, digest.toString())
        }
        val resource = InputStreamResource(inputStream)
        return ResponseEntity.ok().apply {
            headers(httpHeaders)
        }.apply {
            contentLength(blob.length)
        }.apply {
            contentType(MediaType.parseMediaType(MediaType.APPLICATION_OCTET_STREAM_VALUE))
        }.body(resource)
    }

    // start upload a blob file
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
                logger.info("found accessible blob at [$mountableBlob] to mount  [$context,$mount]")
                return ResponseEntity.status(HttpStatus.CREATED).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
                    .header(DOCKER_CONTENT_DIGEST, mount).header(CONTENT_LENGTH, "0")
                    .header(HttpHeaders.LOCATION, location.toString()).build()
            }
        }
        val uuid = repo.startAppend(context)
        val startUrl = "${context.projectId}/${context.repoName}/${context.artifactName}/blobs/uploads/$uuid"
        val location = RepoServiceUtil.getDockerURI(startUrl, httpHeaders)
        return ResponseEntity.status(HttpStatus.ACCEPTED).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
            .header(DOCKER_UPLOAD_UUID, uuid).header(HttpHeaders.LOCATION, location.toString()).build()
    }

    // upload a blob file
    override fun uploadBlob(context: RequestContext, digest: DockerDigest, uuid: String, file: ArtifactFile): DockerResponse {
        repoUtil.loadContext(context)
        return if (RepoServiceUtil.putHasStream(httpHeaders)) {
            uploadBlobFromPut(context, digest, file)
        } else {
            finishPatchUpload(context, digest, uuid)
        }
    }

    // patch upload file
    override fun patchUpload(context: RequestContext, uuid: String, file: ArtifactFile): DockerResponse {
        with(context) {
            logger.debug("patch upload blob [$context, $uuid]")
            repoUtil.loadContext(context)
            val appendId = repo.writeAppend(uuid, file, context)
            val url = "$projectId/$repoName/$artifactName/blobs/uploads/$uuid"
            val location = RepoServiceUtil.getDockerURI(url, httpHeaders)
            return ResponseEntity.status(HttpStatus.ACCEPTED).header(CONTENT_LENGTH, "0")
                .header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION).header(DOCKER_UPLOAD_UUID, uuid)
                .header(HttpHeaders.LOCATION, location.toString()).header(HttpHeaders.RANGE, "0-" + (appendId - 1L))
                .build()
        }
    }

    // upload not with patch but direct from the put
    private fun uploadBlobFromPut(context: RequestContext, digest: DockerDigest, file: ArtifactFile): DockerResponse {
        val blobPath = context.artifactName + DOCKER_PRE_SUFFIX + "_uploads" + DOCKER_PRE_SUFFIX + digest.fileName()
        if (!repo.canWrite(context)) {
            return RepoServiceUtil.consumeStreamAndReturnError(file.getInputStream())
        }
        logger.info("deploy docker blob [$blobPath] into [$context]")
        val uploadContext = UploadContext(context.projectId, context.repoName, blobPath).sha256(digest.getDigestHex()).artifactFile(file)
        val result = repo.upload(uploadContext)
        if (!result) {
            logger.warn("error upload blob [$blobPath]")
            return DockerV2Errors.blobUploadInvalid(context.artifactName)
        }
        val location = RepoServiceUtil.getDockerURI("${context.artifactName}/blobs/$digest", httpHeaders)
        return ResponseEntity.created(location).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
            .header(DOCKER_CONTENT_DIGEST, digest.toString()).build()
    }

    // the response after finish patch upload
    private fun finishPatchUpload(context: RequestContext, digest: DockerDigest, uuid: String): DockerResponse {
        logger.debug("finish upload blob [$context, $digest,$uuid]")
        val fileName = digest.fileName()
        val blobPath = "/${context.artifactName}/_uploads/$fileName"
        val uploadContext = UploadContext(context.projectId, context.repoName, blobPath)
        repo.finishAppend(uuid, uploadContext)
        val url = "${context.projectId}/$context.repoName}/${context.artifactName}/blobs/$digest"
        val location = RepoServiceUtil.getDockerURI(url, httpHeaders)
        return ResponseEntity.created(location).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
            .header(CONTENT_LENGTH, "0").header(DOCKER_CONTENT_DIGEST, digest.toString()).build()
    }

    // process with manifest list
    private fun processManifestList(context: RequestContext, tag: String, manifestPath: String, digest: DockerDigest, manifestBytes: ByteArray) {
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
                context, digest,
                manifestPath, manifestBytes
            )
            val params = RepoServiceUtil.buildManifestPropertyMap(artifactName, tag, digest, ManifestType.Schema2List)
            uploadContext.metadata(params)
            if (!repo.upload(uploadContext)) {
                throw DockerFileSaveFailedException(manifestPath)
            }
        }
    }

    // determine the manifest type of the file
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

    // first get manifest from digest
    private fun getManifestByDigest(context: RequestContext, digest: DockerDigest): DockerResponse {
        logger.info("fetch docker manifest [$context}] and digest [$digest] ")
        var artifact = ArtifactUtil.getManifestByName(repo, context, DOCKER_MANIFEST)
        artifact?.let {
            val acceptable = RepoServiceUtil.getAcceptableManifestTypes(httpHeaders)
            if (acceptable.contains(ManifestType.Schema2List)) {
                artifact = ArtifactUtil.getManifestByName(repo, context, DOCKER_MANIFEST_LIST) ?: run {
                    return DockerV2Errors.manifestUnknown(digest.toString())
                }
            }
        }
        return contentUtil.buildManifestResponse(httpHeaders, context, context.artifactName, digest, artifact!!.length)
    }

    // then get manifest with tag
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
        val digest = DockerDigest.fromSha256(manifest.sha256!!)
        return contentUtil.buildManifestResponse(httpHeaders, context, manifestPath, digest, manifest.length)
    }
}
