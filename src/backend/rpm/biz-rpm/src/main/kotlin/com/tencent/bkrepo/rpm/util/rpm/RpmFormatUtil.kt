package com.tencent.bkrepo.rpm.util.rpm

import com.tencent.bkrepo.rpm.util.redline.model.FormatWithType
import com.tencent.bkrepo.rpm.util.redline.model.RpmFormat
import org.redline_rpm.ReadableChannelWrapper
import org.redline_rpm.header.Header
import org.redline_rpm.header.RpmType
import org.redline_rpm.header.Signature
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

object RpmFormatUtil {

    @Throws(IOException::class)
    fun getRpmFormat(channel: ReadableByteChannel): RpmFormat {
        val format = FormatWithType()
        val readableChannelWrapper = ReadableChannelWrapper(channel)
        val headerStartKey = readableChannelWrapper.start()

        val lead = readableChannelWrapper.start()
        format.lead.read(readableChannelWrapper)

        val signature = readableChannelWrapper.start()
        var count = format.signature.read(readableChannelWrapper)
        val sigEntry = format.signature.getEntry(Signature.SignatureTag.SIGNATURES)
        var expected = if (sigEntry == null) 0 else (ByteBuffer.wrap(sigEntry.values as ByteArray, 8, 4).int / -16)

        val headerStartPos = readableChannelWrapper.finish(headerStartKey) as Int
        format.header.startPos = headerStartPos
        val headerKey = readableChannelWrapper.start()
        count = format.header.read(readableChannelWrapper)
        val immutableEntry = format.header.getEntry(Header.HeaderTag.HEADERIMMUTABLE)
        expected = if (immutableEntry == null) 0 else (ByteBuffer.wrap(immutableEntry.values as ByteArray, 8, 4).int / -16)
        val headerLength = readableChannelWrapper.finish(headerKey) as Int
        format.header.endPos = headerStartPos + headerLength

        return RpmFormat(headerStartPos, headerStartPos + headerLength, format, RpmType.BINARY)
    }
}
