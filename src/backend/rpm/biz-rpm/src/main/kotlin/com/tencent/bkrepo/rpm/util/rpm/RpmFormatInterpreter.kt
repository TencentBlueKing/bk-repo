package com.tencent.bkrepo.rpm.util.rpm

import com.google.common.collect.Lists
import com.tencent.bkrepo.rpm.util.redline.model.ChangeLog
import com.tencent.bkrepo.rpm.util.redline.model.Entry
import com.tencent.bkrepo.rpm.util.redline.model.RpmFormat
import com.tencent.bkrepo.rpm.util.redline.model.RpmMetadata
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import org.apache.commons.lang.StringUtils
import org.redline_rpm.header.Header
import org.redline_rpm.header.Flags
import org.redline_rpm.header.AbstractHeader
import org.redline_rpm.header.Signature
import org.redline_rpm.header.RpmType
import java.util.LinkedList

class RpmFormatInterpreter {
    private val log: Logger = LoggerFactory.getLogger(RpmFormatInterpreter::class.java)

    fun interpret(rawMetadata: RpmFormat): RpmMetadata {
        log.trace("Interpreting raw RPM metadata for repository index compatibility")
        val header: Header = rawMetadata.format.header
        val signature: Signature = rawMetadata.format.signature
        val rpmMetadata = RpmMetadata()
        rpmMetadata.headerStart = rawMetadata.headerStart
        rpmMetadata.headerEnd = rawMetadata.headerEnd
        rpmMetadata.name = getName(header)!!
        rpmMetadata.architecture = if (rawMetadata.type == RpmType.SOURCE) "src" else getArchitecture(header)!!
//        if (rawMetadata.type == RpmType.SOURCE) {
//            rpmMetadata.architecture = "src"
//        } else {
//            rpmMetadata.architecture = getArchitecture(header)!!
//        }
        rpmMetadata.version = getVersion(header)!!
        rpmMetadata.epoch = getEpoch(header)
        rpmMetadata.release = getRelease(header)!!
        rpmMetadata.summary = getSummary(header)!!
        rpmMetadata.description = getDescription(header)!!
        rpmMetadata.packager = getPackager(header)
        rpmMetadata.url = getUrl(header)
        rpmMetadata.buildTime = getBuildTime(header)
        rpmMetadata.installedSize = getInstalledSize(header)
        rpmMetadata.archiveSize = getArchiveSize(signature)
        rpmMetadata.license = getLicense(header)
        rpmMetadata.vendor = getVendor(header)
        rpmMetadata.group = getGroup(header)!!
        rpmMetadata.sourceRpm = getSourceRpm(header)!!
        rpmMetadata.buildHost = getBuildHost(header)!!
        rpmMetadata.provide = resolveEntriesEntries(header, Header.HeaderTag.PROVIDENAME, Header.HeaderTag.PROVIDEFLAGS, Header.HeaderTag.PROVIDEVERSION)
        rpmMetadata.require = resolveEntriesEntries(header, Header.HeaderTag.REQUIRENAME, Header.HeaderTag.REQUIREFLAGS, Header.HeaderTag.REQUIREVERSION)
        rpmMetadata.conflict = resolveEntriesEntries(header, Header.HeaderTag.CONFLICTNAME, Header.HeaderTag.CONFLICTFLAGS, Header.HeaderTag.CONFLICTVERSION)
        rpmMetadata.obsolete = resolveEntriesEntries(header, Header.HeaderTag.OBSOLETENAME, Header.HeaderTag.OBSOLETEFLAGS, Header.HeaderTag.OBSOLETEVERSION)
        rpmMetadata.files = resolveFiles(header)
        rpmMetadata.changeLogs = resolveChangeLogs(header)
        log.trace("Completed interpretation of raw RPM metadata for repository index compatibility")
        return rpmMetadata
    }

    private fun getName(header: Header): String? {
        return getStringHeader(header, Header.HeaderTag.NAME)
    }

    private fun getArchitecture(header: Header): String? {
        return getStringHeader(header, Header.HeaderTag.ARCH)
    }

    private fun getVersion(header: Header): String? {
        return getStringHeader(header, Header.HeaderTag.VERSION)
    }

    private fun getEpoch(header: Header): Int {
        return getIntHeader(header, Header.HeaderTag.EPOCH)
    }

    private fun getRelease(header: Header): String? {
        return getStringHeader(header, Header.HeaderTag.RELEASE)
    }

    private fun getSummary(header: Header): String? {
        return getStringHeader(header, Header.HeaderTag.SUMMARY)
    }

    private fun getDescription(header: Header): String? {
        return getStringHeader(header, Header.HeaderTag.DESCRIPTION)
    }

    private fun getPackager(header: Header): String? {
        return getStringHeader(header, Header.HeaderTag.PACKAGER)
    }

    private fun getUrl(header: Header): String? {
        return getStringHeader(header, Header.HeaderTag.URL)
    }

    private fun getBuildTime(header: Header): Int {
        return getIntHeader(header, Header.HeaderTag.BUILDTIME)
    }

    private fun getInstalledSize(header: Header): Int {
        return getIntHeader(header, Header.HeaderTag.SIZE)
    }

    private fun getArchiveSize(signature: Signature): Int {
        return getIntHeader(signature, Signature.SignatureTag.PAYLOADSIZE)
    }

    private fun getLicense(header: Header): String? {
        return getStringHeader(header, Header.HeaderTag.LICENSE)
    }

    private fun getVendor(header: Header): String? {
        return getStringHeader(header, Header.HeaderTag.VENDOR)
    }

    private fun getGroup(header: Header): String? {
        return getStringHeader(header, Header.HeaderTag.GROUP)
    }

    private fun getSourceRpm(header: Header): String? {
        return getStringHeader(header, Header.HeaderTag.SOURCERPM)
    }

    private fun getBuildHost(header: Header): String? {
        return getStringHeader(header, Header.HeaderTag.BUILDHOST)
    }

    private fun resolveEntriesEntries(header: Header, namesTag: Header.HeaderTag, flagsTag: Header.HeaderTag, versionsTag: Header.HeaderTag): LinkedList<Entry> {
        val entries: LinkedList<Entry> = Lists.newLinkedList()
        val entryNames = getStringArrayHeader(header, namesTag)
        val entryFlags = getIntArrayHeader(header, flagsTag)
        val entryVersions = getStringArrayHeader(header, versionsTag)
        for (i in entryNames.indices) {
            val entryName = entryNames[i]
            val entry = Entry()
            entryName?.let { entry.name = it }
            if (entryFlags.size > i) {
                val entryFlag = entryFlags[i]
                setEntryFlags(entryFlag, entry)
                if (entryFlag and Flags.PREREQ > 0) {
                    entry.pre = "1"
                }
            }
            if (entryVersions.size > i) {
                setEntryVersionFields(entryVersions[i], entry)
            }
            entries.add(entry)
        }

        return entries
    }

    private fun setEntryFlags(entryFlags: Int, entry: Entry): Int {
        if (entryFlags and Flags.LESS > 0 && entryFlags and Flags.EQUAL > 0) {
            entry.flags = "LE"
        } else if (entryFlags and Flags.GREATER > 0 && entryFlags and Flags.EQUAL > 0) {
            entry.flags = "GE"
        } else if (entryFlags and Flags.EQUAL > 0) {
            entry.flags = "EQ"
        } else if (entryFlags and Flags.LESS > 0) {
            entry.flags = "LT"
        } else if (entryFlags and Flags.GREATER > 0) {
            entry.flags = "GT"
        }
        return entryFlags
    }

    private fun setEntryVersionFields(entryVersion: String?, entry: Entry) {
        if (StringUtils.isNotBlank(entryVersion)) {
            val versionTokens: Array<String> = StringUtils.split(entryVersion, '-')
            val versionValue = versionTokens[0]
            val versionValueTokens: Array<String> = StringUtils.split(versionValue, ':')
            if (versionValueTokens.size > 1) {
                entry.epoch = versionValueTokens[0]
                entry.version = versionValueTokens[1]
            } else {
                entry.epoch = "0"
                entry.version = versionValueTokens[0]
            }
            if (versionTokens.size > 1) {
                val releaseValue = versionTokens[1]
                if (StringUtils.isNotBlank(releaseValue)) {
                    entry.release = releaseValue
                }
            }
        }
    }

    private fun resolveFiles(header: Header): LinkedList<File> {
        val files: LinkedList<File> = Lists.newLinkedList()
        val baseNames = getStringArrayHeader(header, Header.HeaderTag.BASENAMES)
        val baseNameDirIndexes = getIntArrayHeader(header, Header.HeaderTag.DIRINDEXES)

        val dirPaths: ArrayList<Array<String?>> = Lists.newArrayList(getStringArrayHeader(header, Header.HeaderTag.DIRNAMES))
        for (i in baseNames.indices) {
            val baseName = baseNames[i]
            val baseNameDirIndex = baseNameDirIndexes[i]
            val filePath = dirPaths[0][baseNameDirIndex] + baseName
            val dir = dirPaths[0][baseNameDirIndex]?.contains("$filePath/")
            val file = if (dir!!) File(filePath, "dir") else File(filePath)
            files.add(file)
        }
        return files
    }

    private fun resolveChangeLogs(header: Header): LinkedList<ChangeLog> {
        val changeLogs: LinkedList<ChangeLog> = Lists.newLinkedList()
        val changeLogAuthors = getStringArrayHeader(header, Header.HeaderTag.CHANGELOGNAME)
        val changeLogDates = getIntArrayHeader(header, Header.HeaderTag.CHANGELOGTIME)
        val changeLogTexts = getStringArrayHeader(header, Header.HeaderTag.CHANGELOGTEXT)
        for (i in changeLogTexts.indices) {
            val changeLog = ChangeLog(
                    changeLogAuthors[i],
                    changeLogDates[i],
                    changeLogTexts[i]
            )
            changeLogs.add(changeLog)
        }
        return changeLogs
    }

    private fun getStringHeader(header: AbstractHeader, tag: AbstractHeader.Tag): String? {
        val values = getStringArrayHeader(header, tag)
        return if (values.isEmpty()) {
            null
        } else values[0]
    }

    private fun getStringArrayHeader(header: AbstractHeader, tag: AbstractHeader.Tag): Array<String?> {
        val entry = header.getEntry(tag) ?: return arrayOfNulls(0)
        return entry.values as Array<String?>
    }

    private fun getIntHeader(header: AbstractHeader, tag: AbstractHeader.Tag): Int {
        val values = getIntArrayHeader(header, tag)
        return if (values.isEmpty()) {
            0
        } else values[0]
    }

    private fun getIntArrayHeader(header: AbstractHeader, tag: AbstractHeader.Tag): IntArray {
        val entry = header.getEntry(tag) ?: return IntArray(0)
        return entry.values as IntArray
    }
}
