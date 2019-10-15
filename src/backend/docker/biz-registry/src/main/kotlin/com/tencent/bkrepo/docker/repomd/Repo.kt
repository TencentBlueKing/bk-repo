package com.tencent.bkrepo.docker.repomd

import java.io.InputStream
import javax.ws.rs.core.Response

interface Repo<C : WorkContext> {
    fun getRepoId(): String

    fun getWorkContextC(): C

    fun artifact(var1: String): Artifact?

//    @Deprecated("")
//    fun artifact(var1: String, var2: Multimap<String, *>): Artifact?
//
//    fun children(var1: String): Iterable<DirectoryItem>?
//
    fun findArtifacts(var1: String, var2: String): Iterable<Artifact>
//
//    fun findArtifacts(var1: Map<String, String>): Iterable<Artifact>
//
//    @Deprecated("")
    fun findArtifacts(var1: String): Iterable<Artifact>

//    fun read(var1: String): InputStream

    fun write(var1: String, var2: InputStream)

    fun delete(var1: String): Boolean

//    fun download(var1: DownloadContext): Response

    fun upload(var1: UploadContext): Response

    fun copy(var1: String, var2: String): Boolean

    fun move(var1: String, var2: String): Boolean

    fun getAttribute(var1: String, var2: String): Any
//
//    fun getAttributes(var1: String, var2: String): Set<*>
//
    fun setAttribute(var1: String, var2: String, var3: Any)

    fun setAttributes(var1: String, var2: String, vararg var3: Any)
//
//    fun addAttribute(var1: String, var2: String, vararg var3: Any)
//
//    fun removeAttribute(var1: String, var2: String, vararg var3: Any)
//
    fun setAttributes(var1: String, var2: Map<String, String>)

    fun exists(var1: String): Boolean

    fun canRead(var1: String): Boolean

    fun canWrite(var1: String): Boolean
}
