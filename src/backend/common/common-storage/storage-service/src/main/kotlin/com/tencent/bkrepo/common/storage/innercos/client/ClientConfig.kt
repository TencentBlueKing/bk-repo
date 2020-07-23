package com.tencent.bkrepo.common.storage.innercos.client

import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.innercos.cl5.CL5Info
import com.tencent.bkrepo.common.storage.innercos.endpoint.CL5EndpointResolver
import com.tencent.bkrepo.common.storage.innercos.endpoint.DefaultEndpointResolver
import com.tencent.bkrepo.common.storage.innercos.endpoint.EndpointResolver
import com.tencent.bkrepo.common.storage.innercos.endpoint.RegionEndpointBuilder
import com.tencent.bkrepo.common.storage.innercos.http.HttpProtocol
import org.springframework.util.unit.DataSize
import java.time.Duration

class ClientConfig(private val credentials: InnerCosCredentials) {
    val maxUploadParts: Int = 10000
    val signExpired: Duration = Duration.ofDays(1)
    val httpProtocol: HttpProtocol = HttpProtocol.HTTP

    val multipartUploadThreshold: Long = DataSize.ofMegabytes(10).toBytes()
    val minimumUploadPartSize: Long = DataSize.ofMegabytes(10).toBytes()

    val endpointResolver = createEndpointResolver()
    val endpointBuilder = RegionEndpointBuilder()

    private fun createEndpointResolver(): EndpointResolver {
        return if (credentials.modId != null && credentials.cmdId != null) {
            val cl5Info = CL5Info(credentials.modId!!, credentials.cmdId!!, credentials.timeout)
            CL5EndpointResolver(cl5Info)
        } else {
            DefaultEndpointResolver()
        }
    }
}
