package com.tencent.bkrepo.common.storage.innercos.request

import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.innercos.client.ClientConfig
import com.tencent.bkrepo.common.storage.innercos.endpoint.EndpointResolver
import com.tencent.bkrepo.common.storage.innercos.http.Headers
import com.tencent.bkrepo.common.storage.innercos.http.HttpProtocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("COS 请求签名测试")
class CosRequestTest {

    @Test
    fun `pathStyle disabled keeps domain host and original uri`() {
        val credentials = credentials()
        val config = ClientConfig(credentials).apply {
            httpProtocol = HttpProtocol.HTTPS
            endpointResolver = FixedResolver(RESOLVED_IP_WITH_DEFAULT_PORT)
        }
        val request = GetObjectRequest(KEY)
        val authorization = request.sign(credentials, config)

        assertEquals("https://$RESOLVED_IP_WITH_DEFAULT_PORT/$KEY", request.url)
        assertEquals(ENDPOINT, request.headers[Headers.HOST])
        assertEquals("/$KEY", request.getFormatUri())
        assertTrue(authorization.contains("q-header-list=host"))
        assertFalse(request.url.contains("/$BUCKET/"))
    }

    @Test
    fun `pathStyle uses ip as host and strips default http port`() {
        val credentials = credentials()
        val config = ClientConfig(credentials).apply {
            pathStyle = true
            httpProtocol = HttpProtocol.HTTP
            endpointResolver = FixedResolver(RESOLVED_IP_WITH_DEFAULT_PORT)
        }
        val request = GetObjectRequest(KEY)
        val authorization = request.sign(credentials, config)

        assertEquals("http://$RESOLVED_IP/$BUCKET/$KEY", request.url)
        assertEquals(RESOLVED_IP, request.headers[Headers.HOST])
        assertEquals("/$BUCKET/$KEY", request.getFormatUri())
        assertTrue(authorization.contains("q-header-list=host"))
    }

    @Test
    fun `pathStyle keeps non-default port in host and url`() {
        val credentials = credentials()
        val config = ClientConfig(credentials).apply {
            pathStyle = true
            httpProtocol = HttpProtocol.HTTP
            endpointResolver = FixedResolver(RESOLVED_IP_WITH_CUSTOM_PORT)
        }
        val request = GetObjectRequest(KEY)
        val authorization = request.sign(credentials, config)

        assertEquals("http://$RESOLVED_IP_WITH_CUSTOM_PORT/$BUCKET/$KEY", request.url)
        assertEquals(RESOLVED_IP_WITH_CUSTOM_PORT, request.headers[Headers.HOST])
        assertTrue(authorization.contains("q-header-list=host"))
    }

    private fun credentials() = InnerCosCredentials(
        secretId = "secretId",
        secretKey = "secretKey",
        region = REGION,
        bucket = BUCKET
    )

    private class FixedResolver(private val host: String) : EndpointResolver {
        override fun resolveEndpoint(endpoint: String) = host
    }

    companion object {
        private const val REGION = "gz"
        private const val BUCKET = "mybucket"
        private const val KEY = "abc123"
        private const val ENDPOINT = "mybucket.gz.tencent-cloud.com"
        private const val RESOLVED_IP = "1.2.3.4"
        private const val RESOLVED_IP_WITH_DEFAULT_PORT = "1.2.3.4:80"
        private const val RESOLVED_IP_WITH_CUSTOM_PORT = "1.2.3.4:8080"
    }
}
