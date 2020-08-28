package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_TYPE
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata

/**
 * manifest context to describe
 * interactive params
 */
object ManifestContext {

    private const val DOCKER_MANIFEST_DIGEST = "docker.manifest.digest"
    private const val DOCKER_MANIFEST_NAME = "docker.manifest"
    private const val DOCKER_NAME_REPO = "docker.repoName"

    /**
     * build the docker image property
     * @param dockerRepo docker image name
     * @param tag docker tag
     * @param digest docker image digest
     * @param type docker image manifest type
     * @return HashMap metadata property
     */
    fun buildPropertyMap(
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

    /**
     * build manifest list upload context
     * @param context the request context
     * @param digest the digest of docker image
     * @param path docker path
     * @param bytes byte data of manifest
     */
    fun buildManifestListUploadContext(
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

    /**
     * build the manifest upload context
     * @param context the request context
     * @param type the manifest file type
     * @param metadata the metadata of manifest
     * @param path the manifest path
     * @param file upload  file object
     */
    fun buildUploadContext(
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
}
