/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata.service.util

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.metadata.annotation.Sensitive
import com.tencent.bkrepo.common.metadata.handler.MaskString
import com.tencent.bkrepo.common.metadata.util.DesensitizedUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class DesensitizedUtilsTest {

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testDesensitizeObject() {
        assert(DesensitizedUtils.desensitizeObject(create()) as Map<String, Any?>)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testConvertMethodArgsToMap() {
        val method1 = TestMethod::class.java.getDeclaredMethod("testMethod1", Child::class.java)
        val desensitizedMap = DesensitizedUtils.convertMethodArgsToMap(method1, arrayOf(create()), true)
        Assertions.assertNull(desensitizedMap["child"])
//
        val method2 = TestMethod::class.java.getDeclaredMethod("testMethod2", Child::class.java)
        val desensitizedMap2 = DesensitizedUtils.convertMethodArgsToMap(method2, arrayOf(create()), true)
        assert(desensitizedMap2["child"] as Map<String, Any?>)
//
        val desensitizedMap3 = DesensitizedUtils.convertMethodArgsToMap(method1, arrayOf(create()), false)
        println(desensitizedMap3)
        val child = desensitizedMap3["child"] as Child
        Assertions.assertEquals(child.nickName, "mk")
        Assertions.assertEquals(child.age, 100)
        Assertions.assertEquals(child.password, "pwd-666666")
        Assertions.assertEquals(child.card.cardPassword, "card-pwd-123-1")
    }

    @Test
    fun testToString() {
        val testDataClass = TestDataClass()
        val result = "{normal=normal, password=******, nullPassword=null, emptyPassword=******, elements=[123, 456]}"
        Assertions.assertEquals("TestDataClass=${result}", DesensitizedUtils.toString(testDataClass))
        Assertions.assertEquals("TestNormalClass=${result}", DesensitizedUtils.toString(TestNormalClass()))
        Assertions.assertEquals("SingletonList=[${result}]", DesensitizedUtils.toString(listOf(testDataClass)))
    }

    @Suppress("UnusedPrivateMember", "UNCHECKED_CAST")
    private fun assert(result: Map<String, Any?>) {
        Assertions.assertEquals(listOf(1, 2, 3), result[Child::no.name])
        Assertions.assertNull(result[Child::password.name])
        Assertions.assertEquals(listOf(null, null, null, null), result[Child::credentials.name])
        Assertions.assertEquals("mk", result[Child::nickName.name])
        Assertions.assertEquals("******", result[Child::name.name])
        Assertions.assertNull((result[Child::card.name] as Map<String, Any?>)[ChildCard::cardPassword.name])
        Assertions.assertEquals(0, result[Child::age.name])
    }

    private fun create(): Child {
        val credentials = listOf(
            Credential("appId-12345-1", "secret-88888-1"),
            Credential("appId-12345-2", "secret-88888-2"),
            Credential("appId-12345-3", "secret-88888-3"),
            Credential("appId-12345-4", "secret-88888-4")
        )
        return Child("mike", "mk", "pwd-666666", credentials, listOf(1, 2, 3), ChildCard("card-pwd-123-1"))
    }
}

class TestMethod {
    @Suppress("UnusedPrivateMember")
    private fun testMethod1(@Sensitive child: Child) {
        // do nothing
    }

    @Suppress("UnusedPrivateMember")
    private fun testMethod2(child: Child) {
        // do nothing
    }
}

open class Parent(
    @Sensitive
    open val name: String,
    @Sensitive
    open val nickName: String,
    @Sensitive
    val age: Int
)

data class Child(
    @Sensitive(handler = MaskString::class)
    override val name: String,
    override val nickName: String,
    @Sensitive
    val password: String,
    val credentials: List<Credential>,
    val no: List<Int>,
    val card: ChildCard
) : Parent(name, nickName, 100)

@Sensitive
data class Credential(
    val appId: String,
    val secret: String,
)

open class Card(
    val id: String
)

class ChildCard(
    @Sensitive
    val cardPassword: String
) : Card("id-ccc-1")

data class TestDataClass(
    val normal: String = "normal",
    @Sensitive(MaskString::class)
    val password: String = "123456",
    @Sensitive(MaskString::class)
    val nullPassword: String? = null,
    @Sensitive(MaskString::class)
    val emptyPassword: String = StringPool.EMPTY,
    val elements: List<String> = listOf("123", "456")
)

class TestNormalClass(
    val normal: String = "normal",
    @Sensitive(MaskString::class)
    val password: String = "123456",
    @Sensitive(MaskString::class)
    val nullPassword: String? = null,
    @Sensitive(MaskString::class)
    val emptyPassword: String = StringPool.EMPTY,
    val elements: List<String> = listOf("123", "456")
)
