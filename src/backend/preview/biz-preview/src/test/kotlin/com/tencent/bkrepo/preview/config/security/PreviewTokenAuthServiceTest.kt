package com.tencent.bkrepo.preview.config.security

import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.TEMPORARY_TOKEN_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.time.LocalDateTime

@DisplayName("Preview 临时 token 鉴权")
class PreviewTokenAuthServiceTest {

    private lateinit var authenticationManager: AuthenticationManager
    private lateinit var temporaryTokenClient: ServiceTemporaryTokenClient
    private lateinit var service: PreviewTokenAuthService

    @BeforeEach
    fun setUp() {
        authenticationManager = mockk()
        temporaryTokenClient = mockk(relaxed = true)
        service = PreviewTokenAuthService(authenticationManager, temporaryTokenClient)
        every { authenticationManager.findUserAccount(any()) } returns userInfo(CREATED_BY)
    }

    @Test
    fun `should accept encoded fullPath that matches decoded token path`() {
        val decodedPath = "/dir/文件 name.pdf"
        val encodedPath = "/dir/%E6%96%87%E4%BB%B6%20name.pdf"
        every { authenticationManager.getTokenInfo(TOKEN) } returns previewToken(fullPath = decodedPath)

        val request = previewRequest(artifactPath = encodedPath)

        assertDoesNotThrow { service.authenticateIfPresent(request) }
        assertEquals(CREATED_BY, request.getAttribute(USER_KEY))
    }

    @Test
    fun `should reject request path outside token fullPath`() {
        every { authenticationManager.getTokenInfo(TOKEN) } returns previewToken(fullPath = "/allowed/file.pdf")

        val request = previewRequest(artifactPath = "/other/secret.pdf")

        val exception = assertThrows(ErrorCodeException::class.java) {
            service.authenticateIfPresent(request)
        }
        assertEquals(PreviewMessageCode.PREVIEW_TEMPORARY_TOKEN_OUT_OF_SCOPE, exception.messageCode)
    }

    @Test
    fun `should accept ascii fullPath without encoding`() {
        val path = "/dir/report.pdf"
        every { authenticationManager.getTokenInfo(TOKEN) } returns previewToken(fullPath = path)

        val request = previewRequest(artifactPath = path)

        assertDoesNotThrow { service.authenticateIfPresent(request) }
        assertEquals(CREATED_BY, request.getAttribute(USER_KEY))
    }

    private fun previewRequest(artifactPath: String): MockHttpServletRequest {
        val uri = "/api/file/onlinePreview/$PROJECT_ID/$REPO_NAME$artifactPath"
        return MockHttpServletRequest("GET", uri).apply {
            addHeader(HttpHeaders.AUTHORIZATION, "$TEMPORARY_TOKEN_AUTH_PREFIX$TOKEN")
            remoteAddr = "127.0.0.1"
        }
    }

    private fun previewToken(fullPath: String): TemporaryTokenInfo {
        return TemporaryTokenInfo(
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = fullPath,
            token = TOKEN,
            authorizedUserList = emptySet(),
            authorizedIpList = emptySet(),
            expireDate = LocalDateTime.now().plusHours(1).toString(),
            permits = null,
            type = TokenType.PREVIEW,
            createdBy = CREATED_BY,
        )
    }

    private fun userInfo(userId: String): UserInfo {
        return UserInfo(
            userId = userId,
            name = userId,
            email = null,
            phone = null,
            createdDate = LocalDateTime.now(),
            locked = false,
            admin = false,
            group = false,
        )
    }

    companion object {
        private const val TOKEN = "preview-token-001"
        private const val PROJECT_ID = "project1"
        private const val REPO_NAME = "repo1"
        private const val CREATED_BY = "alice"
    }
}
