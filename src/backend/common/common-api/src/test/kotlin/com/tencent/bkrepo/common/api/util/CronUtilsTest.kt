package com.tencent.bkrepo.common.api.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test


class CronUtilsTest {
    @Test
    fun test() {
        assertTrue(CronUtils.isValidExpression("0-30/2,31-59/1 15 10 ? * 6L"))
        assertTrue(CronUtils.isValidExpression("0 15 10 ? * 6L 2025-2030"))
        assertTrue(CronUtils.isValidExpression("0 15 10 ? * 6L 2025-2030/1,2031-2035/2"))

        assertFalse(CronUtils.isValidExpression("0 0 0 * *"))
        assertFalse(CronUtils.isValidExpression("xxx"))
        assertFalse(CronUtils.isValidExpression("0 15 10 ? * 6L 2025-2100"))
    }
}
