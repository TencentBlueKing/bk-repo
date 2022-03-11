package com.tencent.bkrepo.auth.util

import cn.hutool.crypto.asymmetric.KeyType
import cn.hutool.crypto.asymmetric.RSA

/**
 * RSA 非对称加密工具类
 */
object RsaUtils {
    private val rsa = RSA()
    var privateKey = rsa.privateKeyBase64!!
    var publicKey = rsa.publicKeyBase64!!

    /**
     * 公钥加密
     * @param password 需要解密的密码
     */
    fun encrypt(password: String): String {
        return rsa.encryptBcd(password, KeyType.PublicKey)
    }

    /**
     * 私钥解密，返回解密后的密码
     * @param password 前端加密后的密码
     */
    fun decrypt(password: String): String {
        return rsa.decryptStr(password, KeyType.PrivateKey)
    }
}

fun main() {
    val rsa2 = RSA(null,"MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDkHUwvWNaF9OJ+1h7EhGX6WIpw9OTo75uMiSsaIjRbC8nlY6jS4MFPKT1XEQZQdE83RMd1UC2IH+0gnhagH7byG1/KelQBJJusAJpcjmPBmpzUj6WcIbm1yHShCBqfxYUZeO8eRrySbWURCGxWT5IZ6wcIKSfabez+bH9lxg+DFQIDAQAB")
    println(rsa2.encryptBcd("bkrepo", KeyType.PublicKey))
}