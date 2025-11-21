package com.tencent.bkrepo.common.artifact.crypt

import com.tencent.bk.sdk.crypto.util.SM4Util
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.mock.web.DelegatingServletOutputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream

class HttpDecryptResponseWrapper(
    private val key: String,
    response: HttpServletResponse,
) : HttpServletResponseWrapper(response) {
    override fun getOutputStream(): ServletOutputStream {
        val keyBytes = key.toByteArray()
        return DelegatingServletOutputStream(SM4DecryptOutputStream(super.getOutputStream(), keyBytes))
    }

    override fun setContentLength(len: Int) {
        // empty
    }

    override fun setContentLengthLong(length: Long) {
        // empty
    }

    override fun setHeader(name: String, value: String) {
        if (name == HttpHeaders.ACCEPT_RANGES || name == HttpHeaders.CONTENT_RANGE) {
            return
        }
    }

    override fun setContentType(type: String) {
        super.setContentType(type)
    }

    class SM4DecryptOutputStream(private val outputStream: OutputStream, private val keyBytes: ByteArray) :
        OutputStream() {
        private val iv = ByteArray(16)
        private var writeIvPos = 0
        private lateinit var cipherOutputStream: CipherOutputStream
        override fun write(b: Int) {
            super.write(byteArrayOf(b.toByte()))
        }

        override fun write(b: ByteArray) {
            write(b, 0, b.size)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (writeIvPos < iv.size) {
                val size = (iv.size - writeIvPos).coerceAtMost(len)
                b.copyInto(iv, writeIvPos, off, size)
                writeIvPos += size
                if (writeIvPos == iv.size) {
                    val cipher = SM4Util.creatCipher(keyBytes, iv, Cipher.DECRYPT_MODE)
                    cipherOutputStream = CipherOutputStream(outputStream, cipher)
                    cipherOutputStream.write(b, off + size, len - size)
                }
            } else {
                cipherOutputStream.write(b, off, len)
            }
        }
    }
}
