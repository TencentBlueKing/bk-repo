package com.tencent.bkrepo.fs.service.drive

import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.client.RAuthClient
import com.tencent.bkrepo.common.metadata.service.project.RProjectService
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveProperties
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import com.tencent.bkrepo.fs.server.context.RequestContext
import com.tencent.bkrepo.fs.server.service.PermissionService
import com.tencent.bkrepo.fs.server.service.drive.DriveTemporaryAccessService
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.util.context.Context
import java.net.InetSocketAddress
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@DisplayName("Drive 临时访问")
class DriveTemporaryAccessServiceTest {

    private val rAuthClient: RAuthClient = mock()
    private val permissionService: PermissionService = mock()
    private val projectService: RProjectService = mock()
    private val driveProperties = DriveProperties()

    private val service = DriveTemporaryAccessService(
        rAuthClient = rAuthClient,
        permissionService = permissionService,
        projectService = projectService,
        driveProperties = driveProperties,
    )

    @Test
    fun `should reject snapSeq query when token does not bind snapSeq`() {
        runBlocking {
            mockTokenInfo(snapSeq = null)
            whenever(permissionService.checkNodePermission(any(), any(), any(), any(), any())).thenReturn(true)
        }

        val exception = assertThrows(ErrorCodeException::class.java) {
            runBlockingWithRequestContext {
                service.validateToken(
                    token = TOKEN,
                    projectId = PROJECT_ID,
                    repoName = REPO_NAME,
                    fullPath = FULL_PATH,
                    type = TokenType.DOWNLOAD,
                    requestSnapSeq = 123L,
                )
            }
        }

        assertEquals(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, exception.messageCode)
    }

    @Test
    fun `should reject mismatched snapSeq`() {
        runBlocking {
            mockTokenInfo(snapSeq = 100L)
            whenever(permissionService.checkNodePermission(any(), any(), any(), any(), any())).thenReturn(true)
        }

        val exception = assertThrows(ErrorCodeException::class.java) {
            runBlockingWithRequestContext {
                service.validateToken(
                    token = TOKEN,
                    projectId = PROJECT_ID,
                    repoName = REPO_NAME,
                    fullPath = FULL_PATH,
                    type = TokenType.DOWNLOAD,
                    requestSnapSeq = 200L,
                )
            }
        }

        assertEquals(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, exception.messageCode)
    }

    @Test
    fun `should accept matching snapSeq`() {
        runBlocking {
            mockTokenInfo(snapSeq = 100L)
            whenever(permissionService.checkNodePermission(any(), any(), any(), any(), any())).thenReturn(true)
        }

        runBlockingWithRequestContext {
            val tokenInfo = service.validateToken(
                token = TOKEN,
                projectId = PROJECT_ID,
                repoName = REPO_NAME,
                fullPath = FULL_PATH,
                type = TokenType.DOWNLOAD,
                requestSnapSeq = 100L,
            )
            assertEquals(100L, tokenInfo.snapSeq)
        }
    }

    @Test
    fun `should use token creator as audited user for anonymous request`() {
        runBlocking {
            mockTokenInfo(snapSeq = null)
            whenever(permissionService.checkNodePermission(any(), any(), any(), any(), any())).thenReturn(true)
        }

        runBlockingWithRequestContext(uidHeader = "consumer") {
            service.validateToken(
                token = TOKEN,
                projectId = PROJECT_ID,
                repoName = REPO_NAME,
                fullPath = FULL_PATH,
                type = TokenType.DOWNLOAD,
                requestSnapSeq = null,
            )
            val exchange = ReactiveRequestContextHolder.getWebExchange()
            assertEquals(USER_ID, exchange.attributes[USER_KEY])
        }
    }

    @Test
    fun `should delete token when permits reaches one`() {
        val tokenInfo = tokenInfo(permits = 1)
        whenever(rAuthClient.deleteTemporaryToken(TOKEN)).thenReturn(Mono.just(Response(0, null, null, null)))

        runBlocking {
            service.decrementPermits(tokenInfo)
        }

        verify(rAuthClient).deleteTemporaryToken(TOKEN)
    }

    private fun mockTokenInfo(snapSeq: Long?) {
        whenever(rAuthClient.getTemporaryTokenInfo(TOKEN)).thenReturn(
            Mono.just(Response(0, null, tokenInfo(snapSeq = snapSeq), null))
        )
    }

    private fun tokenInfo(snapSeq: Long? = null, permits: Int? = null): TemporaryTokenInfo {
        return TemporaryTokenInfo(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = FULL_PATH,
            token = TOKEN,
            authorizedUserList = emptySet(),
            authorizedIpList = emptySet(),
            expireDate = LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_DATE_TIME),
            permits = permits,
            type = TokenType.DOWNLOAD,
            createdBy = USER_ID,
            snapSeq = snapSeq,
        )
    }

    private fun runBlockingWithRequestContext(uidHeader: String? = null, block: suspend () -> Unit) {
        runBlocking {
            val headers = HttpHeaders()
            uidHeader?.let { headers.set(AUTH_HEADER_UID, it) }
            val request = mock<ServerHttpRequest>()
            val response = mock<ServerHttpResponse>()
            val exchange = mock<ServerWebExchange>()
            whenever(request.headers).thenReturn(headers)
            whenever(request.remoteAddress).thenReturn(InetSocketAddress("127.0.0.1", 8080))
            whenever(exchange.attributes).thenReturn(mutableMapOf())
            val requestContext = RequestContext(request, response, exchange)
            withContext(ReactorContext(Context.of(ReactiveRequestContextHolder.REQUEST_CONTEXT_KEY, requestContext))) {
                block()
            }
        }
    }

    companion object {
        private const val PROJECT_ID = "demo"
        private const val REPO_NAME = "drive-local"
        private const val FULL_PATH = "/a.txt"
        private const val TOKEN = "abc123"
        private const val USER_ID = "admin"
    }
}
