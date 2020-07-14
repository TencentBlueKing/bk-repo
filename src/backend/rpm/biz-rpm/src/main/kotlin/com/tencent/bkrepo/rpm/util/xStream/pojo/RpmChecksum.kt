package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAsAttribute
import com.thoughtworks.xstream.annotations.XStreamConverter
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter

/**
 * @property checksum 文件校验信息
 */
@XStreamConverter(value = ToAttributedValueConverter::class, strings = ["checksum"])
data class RpmChecksum(
    val checksum: String
) {
    @XStreamAsAttribute
    val type: String = "sha"
    @XStreamAsAttribute
    val pkgid: String = "YES"
}
