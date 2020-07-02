package com.tencent.bkrepo.common.storage

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ClientCacheTest {

    private val clientCache: LoadingCache<FileSystemCredentials, String> by lazy {
        val cacheLoader = object : CacheLoader<FileSystemCredentials, String>() {
            override fun load(credentials: FileSystemCredentials): String = onCreateClient(credentials)
        }
        CacheBuilder.newBuilder().maximumSize(3).build(cacheLoader)
    }

    private fun onCreateClient(credentials: FileSystemCredentials): String {
        return credentials.toString()
    }

    @Test
    fun test() {
        Assertions.assertEquals(0, clientCache.size())

        val credentials = FileSystemCredentials(path = "data")
        clientCache.get(credentials)
        Assertions.assertEquals(1, clientCache.size())
        clientCache.get(credentials)
        Assertions.assertEquals(1, clientCache.size())

        val sameCredentials = FileSystemCredentials(path = "data")
        clientCache.get(sameCredentials)
        Assertions.assertEquals(1, clientCache.size())

        val anotherCredential = FileSystemCredentials(path = "data2")
        clientCache.get(anotherCredential)
        Assertions.assertEquals(2, clientCache.size())

        val credentials3 = FileSystemCredentials(path = "data").apply { upload.location = "123" }
        clientCache.get(credentials3)
        Assertions.assertEquals(3, clientCache.size())

        val credentials4 = FileSystemCredentials(path = "data").apply { upload.location = "1231" }
        clientCache.get(credentials4)
        Assertions.assertEquals(3, clientCache.size())

    }

    @Test
    fun testHashCode() {
        val credentials1 = FileSystemCredentials(path = "data")
        val hashCode1 = credentials1.hashCode()
        println(hashCode1)

        val credentials2 = FileSystemCredentials(path = "data")
        val hashCode2 = credentials2.hashCode()
        println(hashCode2)

        Assertions.assertEquals(hashCode1, hashCode2)

        val credentials3 = FileSystemCredentials(path = "data2")
        val hashCode3 = credentials3.hashCode()
        println(hashCode3)

        Assertions.assertNotEquals(hashCode1, hashCode3)

        val credentials4 = FileSystemCredentials(path = "data")
        credentials4.upload.location = "123"
        val hashCode4 = credentials4.hashCode()
        println(hashCode4)

        Assertions.assertNotEquals(hashCode1, hashCode4)
    }

}