package com.tencent.bkrepo.common.storage.innercos.endpoint

interface EndpointBuilder {

    fun buildEndpoint(region: String, bucket: String): String
}
