package com.tencent.bkrepo.common.artifact.auth.basic

import com.tencent.bkrepo.common.artifact.auth.core.AuthCredentials

/**
 * Http Basic认证账号
 *
 * @author: carrypan
 * @date: 2019/12/22
 */
data class BasicAuthCredentials(val username: String, val password: String) : AuthCredentials()
