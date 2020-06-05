package com.tencent.bkrepo.rpm.util.redline

import org.redline_rpm.header.Lead
import org.redline_rpm.header.Signature
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel

class Header(
    private val lead: Lead,
    private val signature: Signature,
    private val header: Header
) {
    @Throws(IOException::class)
    fun read(channel: ReadableByteChannel) {
        lead.read(channel)
        signature.read(channel)
        header.read(channel)
    }

    @Throws(IOException::class)
    fun write(channel: FileChannel) {
        lead.write(channel)
        signature.write(channel)
        header.write(channel)
    }

    override fun toString(): String {
        return lead.toString() + signature + header
    }
}
