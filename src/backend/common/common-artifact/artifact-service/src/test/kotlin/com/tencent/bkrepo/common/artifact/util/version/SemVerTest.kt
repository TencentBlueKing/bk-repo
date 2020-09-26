package com.tencent.bkrepo.common.artifact.util.version

import org.junit.jupiter.api.Test

class SemVerTest {

    @Test
    fun testSemVer() {
        println(SemVer.parse("0.0.1"))
        println(SemVer.parse("0.1"))
        println(SemVer.parse("0.1.1"))
        println(SemVer.parse("0"))
        println(SemVer.parse("1.0"))
        println(SemVer.parse("1.1.0"))
        println(SemVer.parse("1"))
        println(SemVer.parse("1.1-alpha-2"))
    }

    @Test
    fun testSemVerOrdinal() {
        println(SemVer.parse("0.0.1").ordinal(4))
        println(SemVer.parse("1.0.1").ordinal(4))
        println(SemVer.parse("1.2.1").ordinal(4))
        println(SemVer.parse("1.2.1-SNAPSHOT").ordinal(4))
    }

}