package com.tencent.bkrepo.archive.utils

import java.io.InputStream
import java.io.OutputStream
import org.tukaani.xz.FilterOptions
import org.tukaani.xz.XZInputStream
import org.tukaani.xz.XZOutputStream

object XZUtils {
    fun compress(input: InputStream, output: OutputStream, opts: FilterOptions) {
        val xzOutputStream = XZOutputStream(output, opts)
        input.copyTo(xzOutputStream)
        xzOutputStream.finish()
    }

    fun decompress(input: InputStream, output: OutputStream) {
        val xzInputStream = XZInputStream(input)
        xzInputStream.copyTo(output)
    }
}
