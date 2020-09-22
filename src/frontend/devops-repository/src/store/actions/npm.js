// import Vue from 'vue'
export default {
    getNpmPkgList (_, { projectId, repoName, dockerName, current = 1, limit = 10 }) {
        return new Promise((resolve, reject) => {
            setTimeout(() => {
                resolve({
                    totalRecords: 2,
                    records: [
                        {
                            'createdBy': 'anonymous',
                            'createdDate': '2020-09-10T11:10:47.934',
                            'lastModifiedBy': 'anonymous',
                            'lastModifiedDate': '2020-09-10T11:10:47.934',
                            'name': 'test3',
                            'size': 0,
                            'stageTag': '@prerelease',
                            'latest': '1.9'
                        },
                        {
                            'createdBy': 'anonymous',
                            'createdDate': '2020-09-10T11:10:47.934',
                            'lastModifiedBy': 'anonymous',
                            'lastModifiedDate': '2020-09-10T11:10:47.934',
                            'name': 'test4',
                            'size': 0,
                            'stageTag': '@prerelease',
                            'latest': '2.6'
                        }
                    ]
                })
            }, 1000)
        })
        // return Vue.prototype.$ajax.get(
        //     `${prefix}/repo/${projectId}/${repoName}`,
        //     {
        //         params: {
        //             pageNumber: current,
        //             pageSize: limit,
        //             name: dockerName
        //         }
        //     }
        // )
    },
    getNpmPkgVersionList (_, { projectId, repoName, dockerName, version, current = 1, limit = 1000 }) {
        return new Promise((resolve, reject) => {
            setTimeout(() => {
                resolve({
                    totalRecords: 2,
                    records: [
                        {
                            'version': '1.9',
                            'stageTag': '',
                            'size': 527,
                            'lastModifiedBy': 'admin',
                            'lastModifiedDate': '2020-09-15T09:41:10.914',
                            'downloadCount': 0
                        },
                        {
                            'version': '2.6',
                            'stageTag': '@prerelease',
                            'size': 527,
                            'lastModifiedBy': 'admin',
                            'lastModifiedDate': '2020-09-15T09:41:10.914',
                            'downloadCount': 0
                        }
                    ]
                })
            }, 1000)
        })
        // return Vue.prototype.$ajax.get(
        //     `${prefix}/tag/${projectId}/${repoName}/${dockerName}`,
        //     {
        //         params: {
        //             pageNumber: current,
        //             pageSize: limit,
        //             tag: tagName
        //         }
        //     }
        // )
    },
    //
    getNpmPkgVersionDetail (_, { projectId, repoName, dockerName, tagName }) {
        return new Promise((resolve, reject) => {
            setTimeout(() => {
                resolve({
                    'basic': {
                        'size': 28593501,
                        'tag': 'latest',
                        'lastModifiedBy': 'admin',
                        'lastModifiedDate': '2020-09-11T14:24:53.788',
                        'downloadCount': 0,
                        'sha256': 'adafef2e596ef06ec2112bc5a9663c6a4f59a3dfd4243c9cabe06c8748e7f288',
                        'os': 'linux'
                    },
                    'history': [{
                        'created': '2020-07-06T21:56:28.828661061Z',
                        'created_by': '/bin/sh -c #(nop) ADD file:cf87af1f0e27aa6ffad26c57edca4ca55dc97861590a2d63475085a08d3b0063 in / '
                    }, {
                        'created': '2020-07-06T21:56:29.704325263Z',
                        'created_by': '/bin/sh -c [ -z "$(apt-get indextargets)" ]'
                    }, {
                        'created': '2020-07-06T21:56:30.474974715Z',
                        'created_by': "/bin/sh -c set -xe \t\t&& echo '#!/bin/sh' > /usr/sbin/policy-rc.d \t&& echo 'exit 101' >> /usr/sbin/policy-rc.d \t&& chmod +x /usr/sbin/policy-rc.d \t\t&& dpkg-divert --local --rename --add /sbin/initctl \t&& cp -a /usr/sbin/policy-rc.d /sbin/initctl \t&& sed -i 's/^exit.*/exit 0/' /sbin/initctl \t\t&& echo 'force-unsafe-io' > /etc/dpkg/dpkg.cfg.d/docker-apt-speedup \t\t&& echo 'DPkg::Post-Invoke { \"rm -f /var/cache/apt/archives/*.deb /var/cache/apt/archives/partial/*.deb /var/cache/apt/*.bin || true\"; };' > /etc/apt/apt.conf.d/docker-clean \t&& echo 'APT::Update::Post-Invoke { \"rm -f /var/cache/apt/archives/*.deb /var/cache/apt/archives/partial/*.deb /var/cache/apt/*.bin || true\"; };' >> /etc/apt/apt.conf.d/docker-clean \t&& echo 'Dir::Cache::pkgcache \"\"; Dir::Cache::srcpkgcache \"\";' >> /etc/apt/apt.conf.d/docker-clean \t\t&& echo 'Acquire::Languages \"none\";' > /etc/apt/apt.conf.d/docker-no-languages \t\t&& echo 'Acquire::GzipIndexes \"true\"; Acquire::CompressionTypes::Order:: \"gz\";' > /etc/apt/apt.conf.d/docker-gzip-indexes \t\t&& echo 'Apt::AutoRemove::SuggestsImportant \"false\";' > /etc/apt/apt.conf.d/docker-autoremove-suggests"
                    }, {
                        'created': '2020-07-06T21:56:31.295257919Z',
                        'created_by': "/bin/sh -c mkdir -p /run/systemd && echo 'docker' > /run/systemd/container"
                    }, {
                        'created': '2020-07-06T21:56:31.471255509Z',
                        'created_by': '/bin/sh -c #(nop)  CMD ["/bin/bash"]'
                    }],
                    'metadata': {
                        'docker.manifest': 'latest',
                        'sha256': 'd5a6519d9f048100123c568eb83f7ef5bfcad69b01424f420f17c932b00dea76',
                        'docker.repoName': '/v2/test/blueking',
                        'docker.manifest.digest': 'sha256:d5a6519d9f048100123c568eb83f7ef5bfcad69b01424f420f17c932b00dea76',
                        'docker.manifest.type': 'application/vnd.docker.distribution.manifest.v2+json'
                    },
                    'layers': [{
                        'mediaType': 'application/vnd.docker.image.rootfs.diff.tar.gzip',
                        'size': 28556756,
                        'digest': 'sha256:692c352adcf2821d6988021248da6b276cb738808f69dcc7bbb74a9c952146f7'
                    }, {
                        'mediaType': 'application/vnd.docker.image.rootfs.diff.tar.gzip',
                        'size': 32327,
                        'digest': 'sha256:97058a342707e39028c2597a4306fd3b1a2ebaf5423f8e514428c73fa508960c'
                    }, {
                        'mediaType': 'application/vnd.docker.image.rootfs.diff.tar.gzip',
                        'size': 848,
                        'digest': 'sha256:2821b8e766f41f4f148dc2d378c41d60f3d2cbe6f03b2585dd5653c3873740ef'
                    }, {
                        'mediaType': 'application/vnd.docker.image.rootfs.diff.tar.gzip',
                        'size': 162,
                        'digest': 'sha256:4e643cc37772c094642f3168c56d1fcbcc9a07ecf72dbb5afdc35baf57e8bc29'
                    }]
                })
            }, 1000)
        })
        // return Vue.prototype.$ajax.get(
        //     `${prefix}/tagdetail/${projectId}/${repoName}/${dockerName}/${tagName}`
        // )
    }
}
