package com.tencent.bkrepo.rpm.util.xStream.repomd

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute
import com.thoughtworks.xstream.annotations.XStreamImplicit

/**
 * repomd.xml
 */
@XStreamAlias("repomd")
data class Repomd(
    @XStreamImplicit()
    val repoDatas: List<RepoData>
) {
        @XStreamAsAttribute
        val xmlns: String = "http://linux.duke.edu/metadata/repo"
        @XStreamAsAttribute
        @XStreamAlias("xmlns:rpm")
        val rpm: String = "http://linux.duke.edu/metadata/rpm"
}
