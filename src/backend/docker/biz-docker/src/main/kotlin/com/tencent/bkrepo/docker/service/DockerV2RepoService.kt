package com.tencent.bkrepo.docker.service

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.pojo.DockerImage
import com.tencent.bkrepo.docker.pojo.DockerTag
import com.tencent.bkrepo.docker.response.DockerResponse

/**
 * docker v2 protocol interface
 */
interface DockerV2RepoService {

    fun ping(): DockerResponse

    fun isBlobExists(context: RequestContext, digest: DockerDigest): DockerResponse

    fun getBlob(context: RequestContext, digest: DockerDigest): DockerResponse

    fun startBlobUpload(context: RequestContext, mount: String?): DockerResponse

    fun patchUpload(context: RequestContext, uuid: String, file: ArtifactFile): DockerResponse

    fun uploadBlob(context: RequestContext, digest: DockerDigest, uuid: String, file: ArtifactFile): DockerResponse

    fun uploadManifest(context: RequestContext, tag: String, mediaType: String, file: ArtifactFile): DockerResponse

    fun getManifest(context: RequestContext, reference: String): DockerResponse

    fun deleteManifest(context: RequestContext, reference: String): DockerResponse

    fun getTags(context: RequestContext, maxEntries: Int, lastEntry: String): DockerResponse

    fun catalog(context: RequestContext, maxEntries: Int, lastEntry: String): DockerResponse

    fun getRepoList(context: RequestContext, pageNumber: Int, pageSize: Int): List<DockerImage>

    fun getRepoTagList(context: RequestContext): List<DockerTag>

    fun buildLayerResponse(context: RequestContext, layerId: String): DockerResponse

    fun getManifestString(context: RequestContext, tag: String): String

    fun deleteTag(context: RequestContext, tag: String): Boolean

    fun getRepoTagDetail(context: RequestContext, tag: String): Map<String, Any>?
}
