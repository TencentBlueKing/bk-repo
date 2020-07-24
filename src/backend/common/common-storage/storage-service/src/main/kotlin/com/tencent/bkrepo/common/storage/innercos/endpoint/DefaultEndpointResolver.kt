package com.tencent.bkrepo.common.storage.innercos.endpoint

class DefaultEndpointResolver : EndpointResolver {

    override fun resolveEndpoint(endpoint: String): String {
        return endpoint
    }
}
