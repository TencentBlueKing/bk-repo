package script

import com.tencent.bkrepo.rpm.util.redline.model.RpmMetadata
import com.tencent.bkrepo.rpm.util.redline.model.RpmRepoMd
import groovy.xml.MarkupBuilder

String xmlPackageWrapper(RpmMetadata rpmMetadata) {
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.'package'(type: 'rpm'){
        'name'(rpmMetadata.name)
        'arch'(rpmMetadata.architecture)
        'version'(epoch: rpmMetadata.epoch, ver: rpmMetadata.vendor, rel: rpmMetadata.release)
        'checksum'(type: 'sha', pkgid: 'YES', rpmMetadata.sha1Digest)
        'summary'(rpmMetadata.summary)
        'description'(rpmMetadata.description)
        'time'(rpmMetadata.lastModified)
        'size'('package': rpmMetadata.buildTime, 'installed': rpmMetadata.installedSize, 'archive': rpmMetadata.archiveSize)
        'location'('href': rpmMetadata.artifactRelativePath)
        'format'{
            'rpm:license'(rpmMetadata.license)
            'rpm:group'(rpmMetadata.group)
            'rpm:buildhost'(rpmMetadata.buildHost)
            'rpm:sourcerpm'(rpmMetadata.sourceRpm)
            'rpm:header-range'('start': rpmMetadata.headerStart, 'end': rpmMetadata.headerEnd)
            'rpm:provides' {
                for (provide in rpmMetadata.provide) {
                    'rpm:entry'(name: provide.name)
                }
            }
            'rpm:requires' {
                for(require in rpmMetadata.require){
                    'rpm:entry'('name': require.name, 'flags': require.flags, 'epoch': require.epoch, 'ver': require.version, 'rel': require.release)
                }
            }
            'rpm:conflicts' {
                for(conflict in rpmMetadata.conflict){
                    'rpm:conflict'('name': conflict.name, 'flags': conflict.flags, 'epoch': conflict.epoch, 'ver': conflict.version, 'rel': conflict.release)
                }
            }
            'rpm:obsoletes' {
                for(obsolete in rpmMetadata.obsolete){
                    'rpm:obsolete'('name': obsolete.name, 'flags': obsolete.flags, 'epoch': obsolete.epoch, 'ver': obsolete.version, 'rel': obsolete.release)
                }
            }
        }
    }
    return writer
}

String xmlPackageInit(RpmMetadata rpmMetadata) {
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.'metadata'(xmlns: 'http://linux.duke.edu/metadata/common', 'xmlns:rpm':'http://linux.duke.edu/metadata/rpm', packages: '1') {
        xmlPackageWrapper(rpmMetadata)
    }
    return writer
}

String xmlUpdate(File xmlFile, RpmMetadata rpmMetadata) {
    //readFile
    // xml.add(xmlWrapper(rpmMetadata))
}

String wrapperRepomd(List<RpmRepoMd> rpmRepoMdList) {
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.'repomd'('xmlns': 'http://linux.duke.edu/metadata/repo', 'xmlns:rpm': 'http://linux.duke.edu/metadata/rpm') {
        for (rpmRepoMd in rpmRepoMdList) {
            'data'('type': 'other') {
                'location'('href': 'repodata/' + rpmRepoMd.sha1)
                'checksum'('type': 'sha', 'pkgid': 'YES', 'rpmRepo.sha1')
                'size'(rpmRepoMd.size)
                'timestamp'(rpmRepoMd.lastModified)
                'open-checksum'('type': 'sha', 'pkgid': 'YES', rpmRepoMd.sha1)
                'open-size'(rpmRepoMd.size)
                'revision/'
            }
        }
    }
}



