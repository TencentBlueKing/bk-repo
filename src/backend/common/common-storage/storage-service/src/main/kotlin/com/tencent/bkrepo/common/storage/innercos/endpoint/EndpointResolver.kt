package com.tencent.bkrepo.common.storage.innercos.endpoint

interface EndpointResolver {

    fun resolveEndpoint(endpoint: String): String
}
