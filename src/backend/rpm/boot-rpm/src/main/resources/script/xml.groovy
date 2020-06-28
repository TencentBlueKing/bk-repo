package script

import com.tencent.bkrepo.rpm.util.redline.model.RpmMetadata
import com.tencent.bkrepo.rpm.util.redline.model.RpmMetadataWithOldStream
import com.tencent.bkrepo.rpm.util.redline.model.RpmRepoMd
import groovy.xml.MarkupBuilder
import groovy.xml.XmlUtil
import org.apache.commons.lang.StringUtils

/**
 * 更新"-primary.xml"
 * @param rpmMetadataWithOldStream
 * @return
 */
String updataPrimaryXml(RpmMetadataWithOldStream rpmMetadataWithOldStream) {
    def oldPrimary = new XmlParser().parse(rpmMetadataWithOldStream.oldPrimaryStream)

    def packageCount = oldPrimary.@packages as Integer
    oldPrimary.@packages = packageCount + 1

    def str = new XmlParser().parseText(xmlPackageInit(rpmMetadataWithOldStream.newRpmMetadata))
    //查询 location&&ver&&rel 相同的节点，有则先删除
    def oldNodeList  = oldPrimary.value()
    def newNode = str.value()[0]
    addIndex(oldNodeList, newNode)
    return XmlUtil.serialize(oldPrimary)
}
/**
 * 构件名称是由工具生成，包含了包名,release, version, 并且构件下载时uri是由构件名称自动拼接。
 * 原则上location值相同的话代表为同一构件(在接受请求时对构件进行了查重，进入这里代表构件包名,release, version相同，但包内文件内容不同)，
 * 将新的<package/>替换旧值
 * @param oldPrimary
 * @param newNode
 */
void addIndex(NodeList oldPrimary, Node newNode) {
    for (oldNode in oldPrimary) {
        if (oldNode.location.@href == newNode.location.@href) {
            oldPrimary.remove(oldNode)
            break
        }
    }
    oldPrimary.add(newNode)
}

MarkupBuilder xmlPackageWrapper(RpmMetadata rpmMetadata, MarkupBuilder builder){
    builder."package"(type: "rpm"){
        "name"(rpmMetadata.name)
        "arch"(rpmMetadata.architecture)
        "version"(epoch: rpmMetadata.epoch, ver: rpmMetadata.version, rel: rpmMetadata.release)
        "checksum"(type: "sha", pkgid: "YES", rpmMetadata.sha1Digest)
        "summary"(rpmMetadata.summary)
        "description"(rpmMetadata.description)
        "time"("file":rpmMetadata.lastModified, "build":rpmMetadata.buildTime)
        "size"("package": rpmMetadata.size, "installed": rpmMetadata.installedSize, "archive": rpmMetadata.archiveSize)
        "location"("href": rpmMetadata.artifactRelativePath)
        "format" {
            "rpm:license"(rpmMetadata.license)
            "rpm:group"(rpmMetadata.group)
            "rpm:buildhost"(rpmMetadata.buildHost)
            "rpm:sourcerpm"(rpmMetadata.sourceRpm)
            "rpm:header-range"("start": rpmMetadata.headerStart, "end": rpmMetadata.headerEnd)
            "rpm:provides" {
                for (provide in rpmMetadata.provide) {
                    if (provide.flags) {
                        "rpm:entry"("name": provide.name , flags: provide.flags, "epoch": provide.epoch, "ver": provide.version, "rel": provide.release)
                    } else {
                        "rpm:entry"("name": provide.name )
                    }
                }
            }
            "rpm:requires" {
                for (require in rpmMetadata.require) {
                    if (require.flags) {
                        "rpm:entry"("name": require.name , flags: require.flags, "epoch": require.epoch, "ver": require.version, "rel": require.release)
                    } else {
                        "rpm:entry"("name": require.name )
                    }
                }
            }
            "rpm:conflicts" {
                for(conflict in rpmMetadata.conflict){
                    if (conflict.flags) {
                        "rpm:entry"("name": conflict.name , flags: conflict.flags, "epoch": conflict.epoch, "ver": conflict.version, "rel": conflict.release)
                    } else {
                        "rpm:entry"("name": conflict.name )
                    }
                }
            }
            "rpm:obsoletes" {
                for(obsolete in rpmMetadata.obsolete){
                    if (obsolete.flags) {
                        "rpm:entry"("name": obsolete.name , flags: obsolete.flags, "epoch": obsolete.epoch, "ver": obsolete.version, "rel": obsolete.release)
                    } else {
                        "rpm:entry"("name": obsolete.name )
                    }
                }
            }
            for (i in rpmMetadata.files.indices) {
                if (StringUtils.isEmpty(rpmMetadata.files[i].type)) {
                    "file"(rpmMetadata.files[i].name)
                } else {
                    "file"("type": "dir", rpmMetadata.files[i].name)
                }
            }
        }
    }
    return builder
}

/**
 * 初始化rpm 构件对应的xml
 * @param rpmMetadata
 * @return
 */
String xmlPackageInit(RpmMetadata rpmMetadata) {
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml."metadata"(xmlns: "http://linux.duke.edu/metadata/common", "xmlns:rpm":"http://linux.duke.edu/metadata/rpm", packages: "1") {
        xmlPackageWrapper(rpmMetadata, xml)
    }
    return xmlHeader =  "<?xml version=\"1.0\"?>\n"+writer
}

String wrapperRepomd(List<RpmRepoMd> rpmRepoMdList) {
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml."repomd"("xmlns": "http://linux.duke.edu/metadata/repo", "xmlns:rpm": "http://linux.duke.edu/metadata/rpm") {
        for (rpmRepoMd in rpmRepoMdList) {
            "data"("type": rpmRepoMd.type) {
                "location"("href": rpmRepoMd.location)
                "checksum"("type": "sha", "pkgid": "YES", rpmRepoMd.xmlGZFileSha1)
                "size"(rpmRepoMd.size)
                "timestamp"(rpmRepoMd.lastModified)
                "open-checksum"("type": "sha", "pkgid": "YES", rpmRepoMd.xmlFileSha1)
                "open-size"(rpmRepoMd.size)
                "revision/"
            }
        }
    }
    return "<?xml version=\"1.0\"?>\n"+writer
}



