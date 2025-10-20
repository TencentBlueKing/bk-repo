package com.tencent.bkrepo.common.service.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UrlSensitiveDataMaskerTest {

    @Test
    fun `test mask short token parameter`() {
        val url = "/api/test?token=abc123&name=test"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?token=******&name=test", result)
    }

    @Test
    fun `test mask long token parameter`() {
        val url = "/api/test?token=abcdefghijklmnop&name=test"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?token=abc***nop&name=test", result)
    }

    @Test
    fun `test mask access_token parameter`() {
        val url = "/api/test?access_token=xyz789&id=123"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?access_token=******&id=123", result)
    }

    @Test
    fun `test mask long access_token parameter`() {
        val url = "/api/test?access_token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9&id=123"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?access_token=eyJ***CJ9&id=123", result)
    }

    @Test
    fun `test mask multiple sensitive parameters`() {
        val url = "/api/test?token=abc123&password=secretpassword&name=test&apikey=key123"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?token=******&password=sec***ord&name=test&apikey=key123", result)
    }

    @Test
    fun `test no sensitive parameters`() {
        val url = "/api/test?name=test&id=123"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?name=test&id=123", result)
    }

    @Test
    fun `test no query parameters`() {
        val url = "/api/test"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test", result)
    }

    @Test
    fun `test empty url`() {
        val result = UrlSensitiveDataMasker.maskSensitiveData("")
        assertEquals("", result)
    }

    @Test
    fun `test null url`() {
        val result = UrlSensitiveDataMasker.maskSensitiveData(null)
        assertEquals("", result)
    }

    @Test
    fun `test mask request line with short token`() {
        val requestLine = "GET /api/test?token=abc123&name=test HTTP/1.1"
        val result = UrlSensitiveDataMasker.maskRequestLine(requestLine)
        assertEquals("GET /api/test?token=******&name=test HTTP/1.1", result)
    }

    @Test
    fun `test mask request line with long token`() {
        val requestLine = "GET /api/test?token=abcdefghijklmnop&name=test HTTP/1.1"
        val result = UrlSensitiveDataMasker.maskRequestLine(requestLine)
        assertEquals("GET /api/test?token=abc***nop&name=test HTTP/1.1", result)
    }

    @Test
    fun `test mask request line without protocol`() {
        val requestLine = "POST /api/login?password=secret123456"
        val result = UrlSensitiveDataMasker.maskRequestLine(requestLine)
        assertEquals("POST /api/login?password=sec***456", result)
    }

    @Test
    fun `test case insensitive parameter names`() {
        val url = "/api/test?TOKEN=abc123&Password=secretpassword&API_KEY=key123"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?TOKEN=******&Password=sec***ord&API_KEY=key123", result)
    }

    @Test
    fun `test url encoded parameters`() {
        val url = "/api/test?access%5Ftoken=abc123&normal=value"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?access%5Ftoken=******&normal=value", result)
    }

    @Test
    fun `test parameter without value`() {
        val url = "/api/test?token&name=test"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?token&name=test", result)
    }

    @Test
    fun `test parameter with empty value`() {
        val url = "/api/test?token=&name=test"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?token=******&name=test", result)
    }

    @Test
    fun `test mask very long sensitive parameter`() {
        val url = "/api/test?authorization=Bearer xxxxxxxxxxxxxxxxxw5c"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?authorization=Bea***w5c", result)
    }

    @Test
    fun `test mask boundary length parameter`() {
        // 测试长度刚好为12的参数
        val url = "/api/test?token=123456789012&name=test"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?token=123***012&name=test", result)
    }

    @Test
    fun `test mask boundary length minus one parameter`() {
        // 测试长度为11的参数（小于12）
        val url = "/api/test?token=12345678901&name=test"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?token=******&name=test", result)
    }

    @Test
    fun `test mask single character parameter`() {
        val url = "/api/test?token=a&name=test"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?token=******&name=test", result)
    }

    @Test
    fun `test mask three character parameter`() {
        val url = "/api/test?token=abc&name=test"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?token=******&name=test", result)
    }

    @Test
    fun `test mask jwt token`() {
        val jwtToken = "xxxxxxxxw5c"
        val url = "/api/test?authorization=Bearer $jwtToken&name=test"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        // Bearer + 空格 + JWT token，总长度超过12，应该显示前3位和后3位
        assertEquals("/api/test?authorization=Bea***w5c&name=test", result)
    }

    @Test
    fun `test mask secretkey parameter`() {
        val url = "/api/test?secretkey=mysecretkey123&name=test"
        val result = UrlSensitiveDataMasker.maskSensitiveData(url)
        assertEquals("/api/test?secretkey=mys***123&name=test", result)
    }
}