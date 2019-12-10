package com.tencent.bkrepo.generic.util

import jdk.nashorn.internal.ir.annotations.Ignore
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 节点工具类测试
 *
 * @author: carrypan
 * @date: 2019-09-24
 */
@DisplayName("工具测试")
internal class TokenUtilsTest {

    @Test
    @Ignore
    fun testCreateToken() {
        println(TokenUtils.createToken())
    }
}

