package com.tencent.bkrepo.rpm.util.xStream.repomd

import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmChecksum
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmLocation
import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute

/**
 * @property type 索引类型，已知的有"primary, other, filelists"
 * @property location 索引文件下载链接(相对路径)
 * @property openChecksum 代表'xml.gz'解压之后'xml'文件的sha1值
 * @property checksum 代表'xml.gz'的sha1值，同时也作为'xml.gz'的文件名
 */
@XStreamAlias("data")
data class RepoData(
    @XStreamAsAttribute
    val type: String,
    val location: RpmLocation,
    val checksum: RpmChecksum,
    val size: Long,
    val timestamp: String,
    @XStreamAlias("open-checksum")
    val openChecksum: RpmChecksum,
    @XStreamAlias("open-size")
    val openSize: Int,
    val revision: String = ""
)
