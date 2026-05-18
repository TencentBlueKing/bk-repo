package com.tencent.bkrepo.common.artifact.crypt

import cn.hutool.crypto.asymmetric.KeyType
import cn.hutool.crypto.asymmetric.RSA
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.security.crypto.CryptoProperties
import org.slf4j.LoggerFactory

class CryptKeyDecryptor(
    cryptoProperties: CryptoProperties
) {

    private val rsa = RSA(
        cryptoProperties.rsaAlgorithm,
        cryptoProperties.privateKeyStr2048PKCS1,
        cryptoProperties.publicKeyStr2048PKCS1,
    )


    fun getKey(encryptedKey: String): String {
        val cryptKey = try {
            val data = rsa.decryptStr(encryptedKey, KeyType.PrivateKey)
            data.readJsonString<CryptKey>()
        } catch (e: Exception) {
            logger.warn("Crypt key[$encryptedKey] is invalid:  ${e.message}")
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "crypt key")
        }
        if (cryptKey.timestamp + cryptKey.expiredSeconds < System.currentTimeMillis() / 1000L) {
            logger.info("Crypt key[$encryptedKey] expired, timestamp is ${cryptKey.timestamp}, " +
                    "expired seconds is ${cryptKey.expiredSeconds}")
            throw ErrorCodeException(CommonMessageCode.RESOURCE_EXPIRED, "crypt key")
        }
        return cryptKey.key
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CryptKeyDecryptor::class.java)
    }
}