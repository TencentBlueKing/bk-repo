export default {
    data () {
        return {
            userInfo: {
                username: ''
            }
        }
    },
    computed: {
        projectId () {
            return this.$route.params.projectId
        },
        repoName () {
            return this.$route.query.name
        },
        packageKey () {
            return this.$route.query.package || ''
        },
        packageName () {
            return this.packageKey.replace(/^.*:\/\/(?:.*:)*([^:]+)$/, '$1') || '<PACKAGE_NAME>'
        },
        version () {
            return this.$route.query.version || '<PACKAGE_VERSION>'
        },
        repoUrl () {
            return `${location.origin}/${this.projectId}/${this.repoName}`
        },
        dockerGuide () {
            return [
                {
                    title: '设置凭证',
                    main: [
                        {
                            subTitle: '配置个人凭证',
                            codeList: [`docker login -u ${this.userInfo.username} -p <PERSONAL_ACCESS_TOKEN> ${location.origin}`]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            subTitle: '1、给本地的镜像打标签',
                            codeList: [`docker tag <LOCAL_IMAGE_TAG> ${location.host}/${this.projectId}/${this.repoName}/${this.packageName}`]
                        },
                        {
                            subTitle: '2、推送您的docker 镜像',
                            codeList: [`docker push ${location.host}/${this.projectId}/${this.repoName}/${this.packageName}`]
                        }
                    ]
                },
                {
                    title: '下载',
                    main: [
                        {
                            codeList: [`docker pull ${location.host}/${this.projectId}/${this.repoName}/${this.packageName}`]
                        }
                    ]
                }
            ]
        },
        dockerInstall () {
            return [
                {
                    main: [
                        {
                            subTitle: '使用如下命令去拉取包',
                            codeList: [
                                `docker pull ${this.dockerDomain}/${this.projectId}/${this.repoName}/${this.packageName}:${this.version}`
                            ]
                        }
                    ]
                }
            ]
        },
        npmGuide () {
            return [
                {
                    title: '设置凭证',
                    main: [
                        {
                            subTitle: '1、在项目根目录下（与package.json同级），添加文件.npmrc，拷贝如下信息',
                            codeList: [
                                `registry=${this.repoUrl}/`,
                                `always-auth=true`,
                                `//${this.repoUrl}/:username=${this.userInfo.username}`,
                                `//${this.repoUrl}/:password=<PERSONAL_ACCESS_TOKEN>`,
                                `//${this.repoUrl}/:email=<EMAIL>`
                            ]
                        },
                        {
                            subTitle: '2、设置 npm registry为当前制品库仓库，进入命令行根据用户凭证登录',
                            codeList: [
                                `npm registry ${this.repoUrl}/`,
                                `npm login`
                            ]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            codeList: [`npm publish`]
                        }
                    ]
                },
                {
                    title: '下载',
                    main: [
                        {
                            subTitle: '1、在设置仓库地址之后就可以使用如下命令去拉取包',
                            codeList: [`npm install ${this.packageName}`]
                        },
                        {
                            subTitle: '2、也可以通过指定registry的方式去拉取包，如下命令',
                            codeList: [`npm install ${this.packageName} --registry ${this.repoUrl}/`]
                        }
                    ]
                }
            ]
        },
        npmInstall () {
            return [
                {
                    main: [
                        {
                            subTitle: '1、在设置仓库地址之后就可以使用如下命令去拉取包',
                            codeList: [
                                `npm install ${this.packageName}@${this.version}`
                            ]
                        },
                        {
                            subTitle: '2、也可以通过指定registry的方式去拉取包，如下命令',
                            codeList: [
                                `npm install ${this.packageName}@${this.version} --registry ${this.repoUrl}/`
                            ]
                        }
                    ]
                }
            ]
        },
        mavenGuide () {
            return [
                {
                    title: '配置依赖源下载地址',
                    main: [
                        {
                            subTitle: '1、全局配置，conf/setting.xml中添加源地址',
                            codeList: [
                                `<mirror>`,
                                `   <id>${this.projectId}-${this.repoName}</id>`,
                                `   <name>${this.repoName}</name>`,
                                `   <url>${this.repoUrl}/</url>`,
                                `   <mirrorOf>central</mirrorOf>`,
                                `</mirror>`
                            ]
                        },
                        {
                            subTitle: '2、项目设置，项目pom.xml中添加源地址',
                            codeList: [
                                `<repository>`,
                                `   <id>${this.projectId}-${this.repoName}</id>`,
                                `   <url>${this.repoUrl}/</url>`,
                                `</repository>`
                            ]
                        }
                    ]
                },
                {
                    title: '设置凭证',
                    main: [
                        {
                            subTitle: '在配置文件 conf/settings.xml设置账户密码；项目内 settings.xml 也可以设置，高优先级',
                            codeList: [
                                `<servers>`,
                                `   <server>`,
                                `       <id>${this.projectId}-${this.repoName}</id>`,
                                `       <username>${this.userInfo.username}</username>`,
                                `       <password><PERSONAL_ACCESS_TOKEN></password>`,
                                `   </server>`,
                                `</servers>`
                            ]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            subTitle: '配置 pom.xml',
                            codeList: [
                                `<distributionManagement>`,
                                `   <repository>`,
                                `       <!--id值与配置的server id 一致-->`,
                                `       <id>${this.projectId}-${this.repoName}</id>`,
                                `       <name>${this.repoName}</name>`,
                                `       <url>${this.repoUrl}/</url>`,
                                `   </repository>`,
                                `</distributionManagement>`
                            ]
                        },
                        {
                            subTitle: '推送包',
                            codeList: [
                                `mvn deploy`
                            ]
                        }
                    ]
                },
                {
                    title: '拉取',
                    main: [
                        {
                            subTitle: '在maven配置文件 conf/settings.xml配置',
                            codeList: [
                                `<profiles>`,
                                `   <profile>`,
                                `       <id>repository proxy</id>`,
                                `       <activation>`,
                                `           <activeByDefault>true</activeByDefault>`,
                                `       </activation>`,
                                `       <repositories>`,
                                `           <repository>`,
                                `               <id>${this.projectId}-${this.repoName}</id>`,
                                `               <name>${this.repoName}</name>`,
                                `               <url>${this.repoUrl}/</url>`,
                                `               <releases>`,
                                `                   <enabled>true</enabled>`,
                                `               </releases>`,
                                `               <snapshots>`,
                                `                   <enabled>true</enabled>`,
                                `               </snapshots>`,
                                `           </repository>`,
                                `       </repositories>`,
                                `   </profile>`,
                                `</profiles>`
                            ]
                        },
                        {
                            subTitle: '拉取maven包',
                            codeList: [
                                `mvn package`
                            ]
                        }
                    ]
                }
            ]
        },
        mavenInstall () {
            return [
                {
                    main: [
                        {
                            subTitle: 'Apache Maven',
                            codeList: [
                                `<dependency>`,
                                `   <groupId>${this.detail.basic.groupId}</groupId>`,
                                `   <artifactId>${this.detail.basic.artifactId}</artifactId>`,
                                `   <version>${this.version}</version>`,
                                `</dependency>`
                            ]
                        },
                        {
                            subTitle: 'Gradle Groovy DSL',
                            codeList: [
                                `implementation '${this.detail.basic.groupId}:${this.detail.basic.artifactId}:${this.version}'`
                            ]
                        },
                        {
                            subTitle: 'Gradle Kotlin DSL',
                            codeList: [
                                `implementation("${this.detail.basic.groupId}:${this.detail.basic.artifactId}:${this.version}")`
                            ]
                        }
                    ]
                }
            ]
        },
        helmGuide () {
            return [
                {
                    title: '推送',
                    main: [
                        {
                            subTitle: '推送打包后的Chart',
                            codeList: [
                                `curl -X POST -T <mychart.tgz> -u ${this.userInfo.username}:<PERSONAL_ACCESS_TOKEN> "${this.repoUrl}"`
                            ]
                        },
                        {
                            subTitle: '推送Chart Provenance',
                            codeList: [
                                `curl -X POST -T <mychart.tgz.prov> -u ${this.userInfo.username}:<PERSONAL_ACCESS_TOKEN> "${this.repoUrl}?prov=1"`
                            ]
                        }
                    ]
                },
                {
                    title: '下载',
                    main: [
                        {
                            subTitle: '1、配置',
                            codeList: [
                                `helm repo add --username ${this.userInfo.username} ${this.repoName} "${this.repoUrl}"`
                            ]
                        },
                        {
                            subTitle: '2、更新本地repo信息',
                            codeList: [
                                `helm repo update`
                            ]
                        },
                        {
                            subTitle: '3、拉取',
                            codeList: [
                                `helm fetch ${this.repoName}/${this.packageName}`
                            ]
                        }
                    ]
                }
            ]
        },
        helmInstall () {
            return [
                {
                    main: [
                        {
                            subTitle: '1、手动配置',
                            codeList: [
                                `helm repo add --username ${this.userInfo.username} ${this.repoName} "${this.repoUrl}"`
                            ]
                        },
                        {
                            subTitle: '2、更新本地的repo信息',
                            codeList: [
                                `helm repo update`
                            ]
                        },
                        {
                            subTitle: '3、拉取',
                            codeList: [
                                `helm fetch ${this.repoName}/${this.packageName}`
                            ]
                        }
                    ]
                }
            ]
        },
        rpmGuide () {
            return [
                {
                    title: '设置凭证',
                    main: [
                        {
                            subTitle: '配置文件目录：/etc/yum.repos.d/'
                        },
                        {
                            subTitle: '全局默认配置文件：CentOS-Base.repo'
                        },
                        {
                            subTitle: '或者自定义：{name}.repo',
                            codeList: [
                                `[bkrepo]`,
                                `name=bkrepo //仓库名`,
                                `baseurl=http://admin:password@${this.repoUrl}/$releasever/os/$basearch //仓库地址，如果有开启认证，需要在请求前添加 用户名：密码`,
                                `keepcache=0 //是否开启缓存，测试阶段推荐开启，否则上传后，yum install 时会优先去本地缓存找`,
                                `enabled=1 //地址授信，如果非 https 环境必须设为1`,
                                `gpgcheck=0 //设为0，目前还不支持gpg签名`,
                                `metadata_expire=1m //本地元数据过期时间 ，测试阶段数据量不大的话，时间越短测试越方便`
                            ]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            codeList: [
                                `curl -u admin:password -XPUT ${this.repoUrl} -T {文件路径}`
                            ]
                        }
                    ]
                },
                {
                    title: '下载',
                    main: [
                        {
                            codeList: [
                                `yum install -y ${this.packageName}`
                            ]
                        }
                    ]
                }
            ]
        },
        rpmInstall () {
            return [
                {
                    main: [
                        {
                            subTitle: '使用如下命令去拉取包',
                            codeList: [
                                `yum install -y ${this.packageName}:${this.version}`
                            ]
                        }
                    ]
                }
            ]
        },
        pypiGuide () {
            return [
                {
                    title: '设置凭证',
                    main: [
                        {
                            subTitle: '在您的 $HOME/.pip/pip.conf 文件添加以下配置',
                            codeList: [
                                `[global]`,
                                `index-url = ${this.repoUrl}`,
                                `username = ${this.userInfo.username}`,
                                `password = <PERSONAL_ACCESS_TOKEN>`
                            ]
                        }
                    ]
                },
                {
                    title: '上传',
                    main: [
                        {
                            subTitle: '使用twine作为上传工具',
                            codeList: [
                                `python3 -m twine upload --repository-url ${this.repoUrl} [-u user] [-p password] dist/`
                            ]
                        }
                    ]
                },
                {
                    title: '下载',
                    main: [
                        {
                            codeList: [
                                `pip3 install -i ${this.repoUrl} ${this.packageName}==${this.version}`
                            ]
                        }
                    ]
                }
            ]
        },
        pypiInstall () {
            return [
                {
                    main: [
                        {
                            subTitle: '1、在设置仓库地址之后就可以使用如下命令去拉取包',
                            codeList: [
                                `pypi install ${this.packageName}@${this.version}`
                            ]
                        },
                        {
                            subTitle: '2、也可以通过指定registry的方式去拉取包，如下命令',
                            codeList: [
                                `pypi install ${this.packageName}@${this.version} --registry ${this.repoUrl}`
                            ]
                        }
                    ]
                }
            ]
        },
        composerGuide () {
            return [
                {
                    title: '配置仓库地址',
                    main: [
                        {
                            subTitle: '1、全局配置'
                        },
                        {
                            subTitle: '首先把默认的源给禁用掉',
                            codeList: [
                                `composer config -g secure-http false`
                            ]
                        },
                        {
                            subTitle: '再修改镜像源',
                            codeList: [
                                `composer config -g repo.packagist composer ${this.repoUrl}`
                            ]
                        },
                        {
                            subTitle: '修改成功后可以先查看一下配置',
                            codeList: [
                                `composer config -g -l`
                            ]
                        },
                        {
                            subTitle: '2、局部换源（仅对当前项目有效）'
                        },
                        {
                            subTitle: '在当前项目下的composer.json中添加',
                            codeList: [
                                `{`,
                                `   "repositories": [`,
                                `        {`,
                                `           "type": "composer",`,
                                `           "url": "${this.repoUrl}" //第一个源`,
                                `        },`,
                                `        {`,
                                `           "type": "composer",`,
                                `           "url": "${this.repoUrl}" //第二个源`,
                                `        },`,
                                `   ]`,
                                `}`
                            ]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            codeList: [
                                `curl -u ${this.userInfo.username}:<PERSONAL_ACCESS_TOKEN> "${this.repoUrl}" -T filePath`
                            ]
                        }
                    ]
                },
                {
                    title: '下载',
                    main: [
                        {
                            codeList: [
                                `composer require ${this.packageName}`
                            ]
                        }
                    ]
                }
            ]
        },
        composerInstall () {
            return [
                {
                    main: [
                        {
                            subTitle: '使用如下命令去拉取包',
                            codeList: [
                                `composer require ${this.packageName}@${this.version}`
                            ]
                        }
                    ]
                }
            ]
        },
        articleGuide () {
            return this[`${this.$route.params.repoType}Guide`]
        },
        articleInstall () {
            return this[`${this.$route.params.repoType}Install`]
        }
    },
    mounted () {
        this.getUserInfo()
    },
    methods: {
        getUserInfo () {
            if (this.$userInfo) this.userInfo = this.$userInfo
            else {
                setTimeout(() => {
                    this.getUserInfo()
                }, 1000)
            }
        }
    }
}
