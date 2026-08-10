/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
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
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.agent.session

import com.tencent.bkrepo.common.security.exception.PermissionException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class InMemoryAgentSessionStoreTest {

    private val store = InMemoryAgentSessionStore()

    @Test
    fun `session is bound to user and project`() {
        store.bindSession(USER_ID, PROJECT_ID, SESSION_ID)

        assertDoesNotThrow {
            store.assertSessionOwner(USER_ID, PROJECT_ID, SESSION_ID)
        }
        assertThrows(PermissionException::class.java) {
            store.assertSessionOwner(USER_ID, OTHER_PROJECT_ID, SESSION_ID)
        }
        assertThrows(PermissionException::class.java) {
            store.assertSessionOwner(OTHER_USER_ID, PROJECT_ID, SESSION_ID)
        }
    }

    companion object {
        private const val USER_ID = "user"
        private const val OTHER_USER_ID = "other-user"
        private const val PROJECT_ID = "project"
        private const val OTHER_PROJECT_ID = "other-project"
        private const val SESSION_ID = "session"
    }
}
