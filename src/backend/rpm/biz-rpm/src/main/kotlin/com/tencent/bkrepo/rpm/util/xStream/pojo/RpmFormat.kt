package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamImplicit
import java.util.LinkedList

data class RpmFormat(
    @XStreamAlias("rpm:license")
    val license: String? = "FIXME",
    @XStreamAlias("rpm:vendor")
    val vendor: String?,
    @XStreamAlias("rpm:group")
    val group: String?,
    @XStreamAlias("rpm:buildhost")
    val buildhost: String?,
    @XStreamAlias("rpm:sourcerpm")
    val sourcerpm: String?,
    @XStreamAlias("rpm:header-range")
    val headerRange: RpmHeaderRange?,
    @XStreamAlias("rpm:provides")
    val provides: LinkedList<RpmEntry>?,
    @XStreamAlias("rpm:requires")
    val requires: LinkedList<RpmEntry>?,
    @XStreamAlias("rpm:conflicts")
    val conflicts: LinkedList<RpmEntry>?,
    @XStreamAlias("rpm:obsoletes")
    val obsoletes: LinkedList<RpmEntry>?,
    @XStreamImplicit(itemFieldName = "file")
    var files: List<RpmFile>,
    @XStreamImplicit(itemFieldName = "changelog")
    val changeLogs: LinkedList<RpmChangeLog>
)
