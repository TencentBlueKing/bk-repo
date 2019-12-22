package com.tencent.bkrepo.common.artifact.auth

/**
 *
 * @author: carrypan
 * @date: 2019/12/22
 */
data class BasicAuthCredentials(val username: String, val password: String) : AuthCredentials()
