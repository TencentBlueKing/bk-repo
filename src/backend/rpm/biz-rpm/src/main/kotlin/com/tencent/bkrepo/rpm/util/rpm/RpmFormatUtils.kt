package com.tencent.bkrepo.rpm.util.rpm

import com.tencent.bkrepo.rpm.util.redline.model.FormatWithType
import com.tencent.bkrepo.rpm.util.redline.model.RpmFormat
import org.redline_rpm.ReadableChannelWrapper
import org.redline_rpm.header.Header
import org.redline_rpm.header.RpmType
import org.redline_rpm.header.Signature
import org.slf4j.LoggerFactory
import org.springframework.util.StopWatch
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

object RpmFormatUtils {
    private val logger = LoggerFactory.getLogger(RpmFormatUtils::class.java)

    fun resolveRpmFormat(channel: ReadableByteChannel): RpmFormat {
        val stopWatch = StopWatch("getRpmFormat")
        val format = FormatWithType()
        val readableChannelWrapper = ReadableChannelWrapper(channel)
        stopWatch.start("headerStartKey")
        val headerStartKey = readableChannelWrapper.start()
        stopWatch.stop()
        stopWatch.start("lead")
        val lead = readableChannelWrapper.start()
        stopWatch.stop()
        stopWatch.start("formatLead")
        format.lead.read(readableChannelWrapper)
        stopWatch.stop()

        stopWatch.start("signature")
        val signature = readableChannelWrapper.start()
        stopWatch.stop()
        stopWatch.start("count")
        var count = format.signature.read(readableChannelWrapper)
        stopWatch.stop()
        val sigEntry = format.signature.getEntry(Signature.SignatureTag.SIGNATURES)
        var expected = if (sigEntry == null) 0 else (ByteBuffer.wrap(sigEntry.values as ByteArray, 8, 4).int / -16)

        val headerStartPos = readableChannelWrapper.finish(headerStartKey) as Int
        format.header.startPos = headerStartPos
        stopWatch.start("headerKey")
        val headerKey = readableChannelWrapper.start()
        stopWatch.stop()
        stopWatch.start("count2")
        count = format.header.read(readableChannelWrapper)
        stopWatch.stop()
        val immutableEntry = format.header.getEntry(Header.HeaderTag.HEADERIMMUTABLE)
        expected = if (immutableEntry == null) 0 else (ByteBuffer.wrap(immutableEntry.values as ByteArray, 8, 4).int / -16)
        val headerLength = readableChannelWrapper.finish(headerKey) as Int
        format.header.endPos = headerStartPos + headerLength
        if (logger.isDebugEnabled) {
            logger.debug("getRpmFormatStat: $stopWatch")
        }

        return RpmFormat(headerStartPos, headerStartPos + headerLength, format, RpmType.BINARY)
    }
}
