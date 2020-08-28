package com.tencent.bkrepo.rpm.util.xStream.repomd

import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmChecksum
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmLocation
import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute

@XStreamAlias("data")
data class RepoGroup(
    @XStreamAsAttribute
    val type: String,
    val location: RpmLocation,
    val checksum: RpmChecksum,
    val size: Long,
    val timestamp: String,
    val revision: String = ""
) : RepoIndex()
