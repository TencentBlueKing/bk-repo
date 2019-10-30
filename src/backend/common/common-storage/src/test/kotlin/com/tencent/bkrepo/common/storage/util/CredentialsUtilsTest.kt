package com.tencent.bkrepo.common.storage.util

import com.tencent.bkrepo.common.storage.StorageTypeEnum
import com.tencent.bkrepo.common.storage.innercos.InnerCosCredentials
import com.tencent.bkrepo.common.storage.local.LocalStorageCredentials
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

/**
 *
 * @author: carrypan
 * @date: 2019-10-10
 */
internal class CredentialsUtilsTest {

    @Test
    fun readString() {
        val localStr = "{\"path\":\"/data/upload\"}"
        val innerStr = "{\"secretId\":\"secretId\",\"secretKey\":\"secretKey\",\"region\":\"region\",\"bucket\":\"bucket\"}"


        val local = CredentialsUtils.readString(StorageTypeEnum.LOCAL.name, localStr) as LocalStorageCredentials
        assertEquals("/data/upload", local.path)

        val inner = CredentialsUtils.readString(StorageTypeEnum.INNER_COS.name, innerStr) as InnerCosCredentials
        assertEquals("bucket", inner.bucket)
        assertEquals("region", inner.region)
        assertEquals("secretId", inner.secretId)
        assertEquals("secretKey", inner.secretKey)

        assertNull(CredentialsUtils.readString("other", innerStr))

    }

    @Test
    fun writeString() {
        val local = LocalStorageCredentials()
        println(CredentialsUtils.writeString(local))

        val innerCos = InnerCosCredentials()
        innerCos.bucket = "bucket"
        innerCos.region = "region"
        innerCos.secretId = "secretId"
        innerCos.secretKey = "secretKey"
        println(CredentialsUtils.writeString(innerCos))

    }
}