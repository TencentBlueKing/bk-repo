package com.tencent.bkrepo.docker.service

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.response.DockerResponse

/**
 * docker v2 protocol interface
 * @author: owenlxu
 * @date: 2019-10-15
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

    fun getRepoList(context: RequestContext): List<String>

    fun getRepoTagList(context: RequestContext): Map<String, String>

    fun buildLayerResponse(context: RequestContext, layerId: String): DockerResponse

    fun getManifestString(context: RequestContext, tag: String): String
}
