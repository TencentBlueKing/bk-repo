package com.tencent.bkrepo.common.storage.innercos.endpoint

import com.tencent.bkrepo.common.storage.innercos.cl5.CL5Info
import com.tencent.bkrepo.common.storage.innercos.cl5.CL5Utils

class CL5EndpointResolver(private val cl5Info: CL5Info) : EndpointResolver {

    override fun resolveEndpoint(endpoint: String): String {
        return CL5Utils.route(cl5Info).toString()
    }
}
