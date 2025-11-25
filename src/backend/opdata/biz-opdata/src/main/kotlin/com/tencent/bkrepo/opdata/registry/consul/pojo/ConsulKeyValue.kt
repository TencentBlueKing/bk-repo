package com.tencent.bkrepo.opdata.registry.consul.pojo

import org.apache.commons.codec.binary.Base64

data class ConsulKeyValue(
    val LockIndex: Int = 0,
    val Key: String = "",
    val Flags: Int = 0,
    val Value: String? = null,
    val CreateIndex: Int = 0,
    val ModifyIndex: Int = 0
) {
    val decodedValue: String?
        get() = Value?.let { base64Value ->
            String(Base64.decodeBase64(base64Value), Charsets.UTF_8)
        }
}
