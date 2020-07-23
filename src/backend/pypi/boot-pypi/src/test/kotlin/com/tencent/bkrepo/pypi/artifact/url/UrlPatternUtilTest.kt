package com.tencent.bkrepo.pypi.artifact.url

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpServletRequest

@SpringBootTest
class UrlPatternUtilTest {

    val httpServletRequest = MockHttpServletRequest()

    init {
        httpServletRequest.parameterMap
    }


}