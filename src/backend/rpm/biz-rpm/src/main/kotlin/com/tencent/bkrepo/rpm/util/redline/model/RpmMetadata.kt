package com.tencent.bkrepo.rpm.util.redline.model

import java.util.LinkedList
import kotlin.properties.Delegates

class RpmMetadata {
    lateinit var sha1Digest: String
    lateinit var artifactRelativePath: String
    var lastModified: Long? = System.currentTimeMillis()
    var size by Delegates.notNull<Long>()
    var headerStart by Delegates.notNull<Int>()
    var headerEnd by Delegates.notNull<Int>()
    lateinit var name: String
    lateinit var architecture: String
    lateinit var version: String
    var epoch by Delegates.notNull<Int>()
    lateinit var release: String
    lateinit var summary: String
    lateinit var description: String
    var packager: String? = null
    var url: String? = null
    var buildTime by Delegates.notNull<Int>()
    var installedSize by Delegates.notNull<Int>()
    var archiveSize by Delegates.notNull<Int>()
    var license: String? = null
    var vendor: String? = null
    lateinit var sourceRpm: String
    lateinit var buildHost: String
    lateinit var group: String
    lateinit var provide: LinkedList<Entry>
    lateinit var require: LinkedList<Entry>
    lateinit var conflict: LinkedList<Entry>
    lateinit var obsolete: LinkedList<Entry>
    lateinit var files: List<File>
    lateinit var changeLogs: LinkedList<ChangeLog>
}
        