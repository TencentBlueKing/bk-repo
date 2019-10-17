package com.tencent.bkrepo.docker.v2.rest.handler

import com.tencent.bkrepo.docker.v2.model.DockerDigest
import javax.ws.rs.core.Response

interface DockerV2RepoHandler {
    fun ping(): Response

    fun isBlobExists(var1: String, var2: DockerDigest): Response

    fun getBlob(var1: String, var2: DockerDigest): Response

    fun startBlobUpload(projectId :String,repoName:String, name: String, digest: String?): Response
//
//    fun patchUpload(var1: String, var2: String, var3: InputStream): Response
//
//    fun uploadBlob(var1: String, var2: DockerDigest, var3: String, var4: InputStream): Response
//
    fun uploadManifest(var1: String, var2: String, mediaType: String, var3: ByteArray): Response
//
//    fun getManifest(var1: String, var2: String): Response
//
    fun deleteManifest(var1: String, var2: String): Response
//
//    fun getTags(var1: String, var2: Int, var3: String): Response
//
//    fun catalog(var1: Int, var2: String): Response
}
