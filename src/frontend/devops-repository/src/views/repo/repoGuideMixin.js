import { mapState, mapActions } from 'vuex'
export default {
    data () {
        return {
            dockerDomain: ''
        }
    },
    computed: {
        ...mapState(['userInfo']),
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
        repoType () {
            return this.$route.params.repoType
        },
        repoUrl () {
            return `${location.origin}/${this.repoType}/${this.projectId}/${this.repoName}`
        },
        dockerGuide () {
            return [
                {
                    title: '设置凭证',
                    main: [
                        {
                            subTitle: '配置个人凭证',
                            codeList: [`docker login -u ${this.userInfo.username} -p <PERSONAL_ACCESS_TOKEN> ${location.protocol}//${this.dockerDomain}`]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            subTitle: '1、给本地的镜像打标签',
                            codeList: [`docker tag <LOCAL_IMAGE_TAG> ${this.dockerDomain}/${this.projectId}/${this.repoName}/${this.packageName}`]
                        },
                        {
                            subTitle: '2、推送您的docker 镜像',
                            codeList: [`docker push ${this.dockerDomain}/${this.projectId}/${this.repoName}/${this.packageName}`]
                        }
                    ]
                },
                {
                    title: '下载',
                    main: [
                        {
                            codeList: [`docker pull ${this.dockerDomain}/${this.projectId}/${this.repoName}/${this.packageName}`]
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
                            subTitle: '方式一、使用个人令牌'
                        },
                        {
                            subTitle: '在项目根目录下（与package.json同级），添加文件.npmrc，拷贝如下信息',
                            codeList: [
                                `registry=${this.repoUrl}/`,
                                `always-auth=true`,
                                `//${this.repoUrl.split('//')[1]}/:username=${this.userInfo.username}`,
                                `//${this.repoUrl.split('//')[1]}/:_password=<BASE64_ENCODE_PERSONAL_ACCESS_TOKEN>`,
                                `//${this.repoUrl.split('//')[1]}/:email=<EMAIL>`
                            ]
                        },
                        {
                            subTitle: '生成<BASE64_ENCODE_PERSONAL_ACCESS_TOKEN>'
                        },
                        {
                            subTitle: '1、在command/shell命令行窗口运行以下代码',
                            codeList: [
                                `node -e "require('readline') .createInterface({input:process.stdin,output:process.stdout,historySize:0}) .question('PAT> ',p => { b64=Buffer.from(p.trim()).toString('base64');console.log(b64);process.exit(); })"`
                            ]
                        },
                        {
                            subTitle: '2、复制<PERSONAL_ACCESS_TOKEN>至命令行窗口后，按下Enter键'
                        },
                        {
                            subTitle: '3、复制编码后的token，替换<BASE64_ENCODE_PERSONAL_ACCESS_TOKEN>'
                        },
                        {
                            subTitle: '方式二、使用命令行'
                        },
                        {
                            subTitle: '设置 npm registry为当前制品库仓库，进入command/shell命令行窗口根据用户凭证登录',
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
                            subTitle: `请将下列配置添加到您的 /etc/yum.repos.d/${this.repoName}.repo 文件中`,
                            codeList: [
                                `[${this.repoName}]`,
                                `name=${this.repoName}`,
                                `baseurl=${this.repoUrl}`,
                                `username=${this.userInfo.username}`,
                                `password=<PERSONAL_ACCESS_TOKEN>`,
                                `enabled=1`,
                                `gpgcheck=0`
                            ]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            codeList: [
                                `curl -u ${this.userInfo.username}:<PERSONAL_ACCESS_TOKEN> -X PUT ${this.repoUrl} -T ${this.packageName}.rpm`
                            ]
                        }
                    ]
                },
                {
                    title: '下载',
                    main: [
                        {
                            subTitle: '使用RPM或者yum方式拉取包'
                        },
                        {
                            subTitle: 'RPM',
                            codeList: [
                                `rpm -i ${location.protocol}//${this.userInfo.username}:<PERSONAL_ACCESS_TOKEN>@${this.repoUrl}/${this.packageName}.rpm`
                            ]
                        },
                        {
                            subTitle: 'yum',
                            codeList: [
                                `yum install --repo ${this.repoName} ${this.packageName}`
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
                            subTitle: '使用RPM或者yum方式拉取包'
                        },
                        {
                            subTitle: 'RPM',
                            codeList: [
                                `rpm -i ${location.protocol}//${this.userInfo.username}:<PERSONAL_ACCESS_TOKEN>@${this.repoUrl}/${this.packageName}.rpm`
                            ]
                        },
                        {
                            subTitle: 'yum',
                            codeList: [
                                `yum install --repo ${this.repoName} ${this.packageName}`
                            ]
                        }
                    ]
                }
            ]
        },
        pypiGuide () {
            return [
                {
                    title: '发布',
                    main: [
                        {
                            subTitle: '配置文件目录：$HOME/.pypirc',
                            codeList: [
                                `[distutils]`,
                                `index-servers = ${this.repoName}`,
                                `[${this.repoName}]`,
                                `repository: ${this.repoUrl}`,
                                `username: ${this.userInfo.username}`,
                                `password: <PERSONAL_ACCESS_TOKEN>`
                            ]
                        },
                        {
                            subTitle: '执行下面命令',
                            codeList: [
                                `python3 -m twine upload -r ${this.repoName} dist/*`
                            ]
                        }
                    ]
                },
                {
                    title: '拉取',
                    main: [
                        {
                            subTitle: '替换默认依赖源地址'
                        },
                        {
                            subTitle: 'MacOS/Liunx配置目录 :  $HOME/.pip/pip.conf',
                            codeList: [
                                `[global]`,
                                `index-url = ${this.repoUrl}`,
                                `username = ${this.userInfo.username}`,
                                `password = <PERSONAL_ACCESS_TOKEN>`
                            ]
                        },
                        {
                            subTitle: 'Windows配置目录 :  %HOME%/pip/pip.ini',
                            codeList: [
                                `[global]`,
                                `index-url = ${this.repoUrl}`,
                                `username = ${this.userInfo.username}`,
                                `password = <PERSONAL_ACCESS_TOKEN>`
                            ]
                        },
                        {
                            subTitle: '执行下面命令',
                            codeList: [
                                `pip3 install ${this.packageName}==${this.version}`
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
                            subTitle: '通过指定registry的方式去拉取包',
                            codeList: [
                                `pip3 install -i ${this.repoUrl}/simple ${this.packageName}==${this.version}`
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
    async created () {
        this.getDockerDomainHandler()
    },
    watch: {
        repoType (val) {
            this.getDockerDomainHandler()
        }
    },
    methods: {
        ...mapActions(['getDockerDomain']),
        async getDockerDomainHandler () {
            this.repoType === 'docker' && !this.dockerDomain && (this.dockerDomain = await this.getDockerDomain())
        }
    }
}
