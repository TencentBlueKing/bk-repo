package com.tencent.bkrepo.docker.manifest

import com.fasterxml.jackson.databind.JsonNode
import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.docker.artifact.DockerArtifact
import com.tencent.bkrepo.docker.artifact.DockerArtifactRepo
import com.tencent.bkrepo.docker.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_CONTENT_DIGEST
import com.tencent.bkrepo.docker.constant.DOCKER_DIGEST
import com.tencent.bkrepo.docker.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_DIGEST
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_LIST
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_NAME
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_TYPE
import com.tencent.bkrepo.docker.constant.DOCKER_NAME_REPO
import com.tencent.bkrepo.docker.context.DownloadContext
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.errors.DockerV2Errors
import com.tencent.bkrepo.docker.exception.DockerFileSaveFailedException
import com.tencent.bkrepo.docker.exception.DockerNotFoundException
import com.tencent.bkrepo.docker.exception.DockerSyncManifestException
import com.tencent.bkrepo.docker.helpers.DockerManifestDigester
import com.tencent.bkrepo.docker.helpers.DockerManifestSyncer
import com.tencent.bkrepo.docker.model.DockerBlobInfo
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata
import com.tencent.bkrepo.docker.response.DockerResponse
import com.tencent.bkrepo.docker.util.BlobUtil
import com.tencent.bkrepo.docker.util.BlobUtil.getManifestConfigBlob
import com.tencent.bkrepo.docker.util.ResponseUtil
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.ResponseEntity

/**
 * docker content  utility
 * to get detail of manifest or blob
 * @author: owenlxu
 * @date: 2019-10-15
 */
class ManifestProcess constructor(val repo: DockerArtifactRepo) {

    private val objectMapper = JsonUtils.objectMapper

    companion object {
        private val logger = LoggerFactory.getLogger(ManifestProcess::class.java)
    }

    // upload a manifest file by type
    fun uploadManifestByType(
        context: RequestContext,
        tag: String,
        manifestPath: String,
        manifestType: ManifestType,
        artifactFile: ArtifactFile
    ): DockerDigest {
        val manifestBytes = IOUtils.toByteArray(artifactFile.getInputStream())
        val digest = DockerManifestDigester.calc(manifestBytes)
        logger.info("manifest file digest content digest : [$digest]")
        if (ManifestType.Schema2List == manifestType) {
            processManifestList(context, tag, manifestPath, digest!!, manifestBytes)
            return digest
        }

        // process scheme2 manifest
        val metadata = ManifestDeserializer.deserialize(repo, context, tag, manifestType, manifestBytes, digest!!)
        addManifestsBlobs(context, manifestType, manifestBytes, metadata)
        if (!DockerManifestSyncer.sync(repo, metadata, context, tag)) {
            val msg = "fail to sync manifest blobs, cancel manifest upload"
            logger.error(msg)
            throw DockerSyncManifestException(msg)
        }

        logger.info("start to upload manifest : [$manifestType]")
        val uploadContext = buildUploadContext(context, manifestType, metadata, manifestPath, artifactFile)
        val params = buildPropertyMap(context.artifactName, tag, digest, manifestType)
        val labels = metadata.tagInfo.labels
        labels.entries().forEach {
            params[it.key] = it.value
        }
        uploadContext.metadata(params)
        if (!repo.upload(uploadContext)) {
            logger.warn("upload manifest fail [$uploadContext]")
            throw DockerFileSaveFailedException(manifestPath)
        }
        return digest
    }

    fun getManifestType(projectId: String, repoName: String, manifestPath: String): String? {
        return repo.getAttribute(projectId, repoName, manifestPath, DOCKER_MANIFEST_TYPE)
    }

    fun getSchema2ManifestConfigContent(context: RequestContext, bytes: ByteArray, tag: String): ByteArray {
        val manifest = objectMapper.readTree(bytes)
        val digest = manifest.get("config").get(DOCKER_DIGEST).asText()
        val fileName = DockerDigest(digest).fileName()
        val configFile = getManifestConfigBlob(repo, fileName, context, tag) ?: run {
            return ByteArray(0)
        }
        logger.info("get manifest config file [$configFile]")
        val downloadContext = DownloadContext(context).sha256(configFile.sha256!!).length(configFile.length)
        val stream = repo.download(downloadContext)
        stream.use {
            return IOUtils.toByteArray(it)
        }
    }

    fun getSchema2ManifestContent(context: RequestContext, schema2Path: String): ByteArray {
        val manifest = getManifestByName(context, schema2Path) ?: run {
            return ByteArray(0)
        }
        val downloadContext = DownloadContext(context).sha256(manifest.sha256!!).length(manifest.length)
        val stream = repo.download(downloadContext)
        stream.use {
            return IOUtils.toByteArray(it)
        }
    }

    fun getSchema2Path(context: RequestContext, bytes: ByteArray): String {
        val manifestList = objectMapper.readTree(bytes)
        val manifests = manifestList.get("manifests")
        val maniIter = manifests.iterator()
        while (maniIter.hasNext()) {
            val manifest = maniIter.next() as JsonNode
            val platform = manifest.get("platform")
            val architecture = platform.get("architecture").asText()
            val os = platform.get("os").asText()
            if (StringUtils.equals(architecture, "amd64") && StringUtils.equals(os, "linux")) {
                val digest = manifest.get(DOCKER_DIGEST).asText()
                val fileName = DockerDigest(digest).fileName()
                val manifestFile = BlobUtil.getBlobByName(repo, context, fileName) ?: run {
                    return EMPTY
                }
                return BlobUtil.getFullPath(manifestFile)
            }
        }
        return EMPTY
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

    // determine the manifest type of the file
    fun chooseManifestType(context: RequestContext, tag: String, httpHeaders: HttpHeaders): ManifestType {
        val acceptable = ResponseUtil.getAcceptableManifestTypes(httpHeaders)
        if (acceptable.contains(ManifestType.Schema2List)) {
            with(context) {
                val manifestPath = ResponseUtil.buildManifestPath(
                    artifactName,
                    tag,
                    ManifestType.Schema2List
                )
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
    fun getManifestByDigest(context: RequestContext, digest: DockerDigest, headers: HttpHeaders): DockerResponse {
        logger.info("fetch docker manifest [$context}] and digest [$digest] ")
        var artifact = getManifestByName(context, DOCKER_MANIFEST)
        artifact?.let {
            val acceptable = ResponseUtil.getAcceptableManifestTypes(headers)
            if (acceptable.contains(ManifestType.Schema2List)) {
                artifact = getManifestByName(context, DOCKER_MANIFEST_LIST) ?: run {
                    logger.warn("get manifest by name fail [$context,$digest]")
                    return DockerV2Errors.manifestUnknown(digest.toString())
                }
            }
        }
        return buildManifestResponse(headers, context, context.artifactName, digest, artifact!!.length)
    }

    // then get manifest with tag
    fun getManifestByTag(context: RequestContext, tag: String, headers: HttpHeaders): DockerResponse {
        val useManifestType = chooseManifestType(context, tag, headers)
        val manifestPath = ResponseUtil.buildManifestPath(context.artifactName, tag, useManifestType)
        logger.info("get manifest by tag params [$context,$manifestPath]")
        if (!repo.canRead(context)) {
            logger.warn("do not have permission to get [$context,$manifestPath]")
            return DockerV2Errors.unauthorizedManifest(manifestPath)
        }
        val manifest = repo.getArtifact(context.projectId, context.repoName, manifestPath) ?: run {
            logger.warn("the node not exist [$context,$manifestPath]")
            return DockerV2Errors.manifestUnknown(manifestPath)
        }
        logger.debug("get manifest by tag result [$manifest]")
        val digest = DockerDigest.fromSha256(manifest.sha256!!)
        return buildManifestResponse(headers, context, manifestPath, digest, manifest.length)
    }

    // process with manifest list
    private fun processManifestList(
        context: RequestContext,
        tag: String,
        manifestPath: String,
        digest: DockerDigest,
        manifestBytes: ByteArray
    ) {
        val manifestList = ManifestListSchema2Deserializer.deserialize(manifestBytes)
        manifestList?.let {
            val iter = manifestList.manifests.iterator()
            // check every manifest in the repo
            while (iter.hasNext()) {
                val manifest = iter.next()
                val mDigest = manifest.digest
                val manifestFileName = DockerDigest(mDigest!!).fileName()
                BlobUtil.getBlobByName(repo, context, manifestFileName) ?: run {
                    logger.warn("manifest not found [$context, $digest]")
                    throw DockerNotFoundException("manifest list [$digest] miss manifest digest $mDigest. ==>$manifest")
                }
            }
        }
        with(context) {
            val uploadContext = buildManifestListUploadContext(context, digest, manifestPath, manifestBytes)
            val params = buildPropertyMap(artifactName, tag, digest, ManifestType.Schema2List)
            uploadContext.metadata(params)
            if (!repo.upload(uploadContext)) {
                logger.warn("upload manifest list fail [$uploadContext]")
                throw DockerFileSaveFailedException(manifestPath)
            }
        }
    }

    private fun addSchema2Blob(bytes: ByteArray, metadata: ManifestMetadata) {
        val manifest = objectMapper.readTree(bytes)
        val config = manifest.get("config")
        config?.let {
            val digest = config.get(DOCKER_DIGEST).asText()
            val blobInfo = DockerBlobInfo(EMPTY, digest, 0L, EMPTY)
            metadata.blobsInfo.add(blobInfo)
        }
    }

    private fun addSchema2ListBlobs(context: RequestContext, bytes: ByteArray, metadata: ManifestMetadata) {
        val manifestList = JsonUtils.objectMapper.readTree(bytes)
        val manifests = manifestList.get("manifests")
        val manifest = manifests.iterator()

        while (manifest.hasNext()) {
            val manifestNode = manifest.next() as JsonNode
            val digestString = manifestNode.get("platform").get(DOCKER_DIGEST).asText()
            val dockerBlobInfo = DockerBlobInfo(EMPTY, digestString, 0L, EMPTY)
            metadata.blobsInfo.add(dockerBlobInfo)
            val manifestFileName = DockerDigest(digestString).fileName()
            val manifestFile = getManifestByName(context, manifestFileName)
            manifestFile?.let {
                val fullPath = BlobUtil.getFullPath(manifestFile)
                val configBytes = getSchema2ManifestContent(context, fullPath)
                addSchema2Blob(configBytes, metadata)
            }
        }
    }

    // build the manifest upload context
    private fun buildUploadContext(
        context: RequestContext,
        type: ManifestType,
        metadata: ManifestMetadata,
        path: String,
        file: ArtifactFile
    ): UploadContext {
        with(context) {
            val uploadContext = UploadContext(projectId, repoName, path).artifactFile(file)
            if ((type == ManifestType.Schema2 || type == ManifestType.Schema2List)) {
                uploadContext.sha256(metadata.tagInfo.digest!!.getDigestHex())
            }
            return uploadContext
        }
    }

    private fun buildManifestResponse(
        httpHeaders: HttpHeaders,
        context: RequestContext,
        manifestPath: String,
        digest: DockerDigest,
        length: Long
    ): DockerResponse {
        val downloadContext = DownloadContext(context).length(length).sha256(digest.getDigestHex())
        val inputStream = repo.download(downloadContext)
        val inputStreamResource = InputStreamResource(inputStream)
        val contentType = getManifestType(context.projectId, context.repoName, manifestPath)
        httpHeaders.apply {
            set(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
        }.apply {
            set(DOCKER_CONTENT_DIGEST, digest.toString())
        }.apply {
            set(CONTENT_TYPE, contentType)
        }
        logger.info("file [$digest] result length [$length] type [$contentType]")
        return ResponseEntity.ok().headers(httpHeaders).contentLength(length).body(inputStreamResource)
    }

    private fun getManifestByName(context: RequestContext, fileName: String): DockerArtifact? {
        val fullPath = "/${context.artifactName}/$fileName"
        return repo.getArtifact(context.projectId, context.repoName, fullPath) ?: null
    }

    private fun buildManifestListUploadContext(
        context: RequestContext,
        digest: DockerDigest,
        path: String,
        bytes: ByteArray
    ): UploadContext {
        with(context) {
            val artifactFile = ArtifactFileFactory.build(bytes.inputStream())
            return UploadContext(projectId, repoName, path).artifactFile(artifactFile).sha256(digest.getDigestHex())
        }
    }

    private fun buildPropertyMap(
        dockerRepo: String,
        tag: String,
        digest: DockerDigest,
        type: ManifestType
    ): HashMap<String, String> {
        var map = HashMap<String, String>()
        map.apply {
            set(digest.getDigestAlg(), digest.getDigestHex())
        }.apply {
            set(DOCKER_MANIFEST_DIGEST, digest.toString())
        }.apply {
            set(DOCKER_MANIFEST_NAME, tag)
        }.apply {
            set(DOCKER_NAME_REPO, dockerRepo)
        }.apply {
            set(DOCKER_MANIFEST_TYPE, type.toString())
        }
        return map
    }
}
