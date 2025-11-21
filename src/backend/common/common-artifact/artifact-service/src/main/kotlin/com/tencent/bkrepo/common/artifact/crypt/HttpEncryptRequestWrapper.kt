package com.tencent.bkrepo.common.artifact.crypt

import com.tencent.bk.sdk.crypto.util.SM4Util
import com.tencent.bkrepo.common.storage.core.crypto.EncryptInputStream
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import org.springframework.mock.web.DelegatingServletInputStream
import java.security.SecureRandom
import javax.crypto.Cipher

class HttpEncryptRequestWrapper(
    private val key: String, request: HttpServletRequest
) : HttpServletRequestWrapper(request) {

    private val random = SecureRandom()

    override fun getInputStream(): ServletInputStream {
        val keyBytes = key.toByteArray()
        val cipher = createSM4Cipher(keyBytes, Cipher.ENCRYPT_MODE)
        return DelegatingServletInputStream(EncryptInputStream(super.getInputStream(), cipher, DEFAULT_BUFFER_SIZE))
    }


    fun createSM4Cipher(keyBytes: ByteArray, mode: Int): Cipher {
        val iv = getRandomIv()
        return SM4Util.creatCipher(keyBytes, iv, mode)
    }

    private fun getRandomIv(): ByteArray {
        val iv = ByteArray(16)
        random.nextBytes(iv)
        return iv
    }
}