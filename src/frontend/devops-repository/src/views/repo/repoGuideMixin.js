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
            return this.packageKey.replace(/^.*:\/\/(?:.*:)*([^:]+)$/, '$1')
        },
        version () {
            return this.$route.query.version
        },
        dockerGuide () {
            return [
                {
                    title: '设置凭证',
                    main: [
                        {
                            subTitle: '配置个人凭证',
                            codeList: [`docker login -u <账号> ${location.origin}`]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            subTitle: '1、给本地的镜像打标签',
                            codeList: [`docker tag <LOCAL_IMAGE_TAG> ${location.origin}/${this.projectId}/${this.repoName}/<PACKAGE>`]
                        },
                        {
                            subTitle: '2、推送您的docker 镜像',
                            codeList: [`docker push ${location.origin}/${this.projectId}/${this.repoName}/<PACKAGE>`]
                        }
                    ]
                },
                {
                    title: '下载',
                    main: [
                        {
                            codeList: [`docker pull ${location.origin}/${this.projectId}/${this.repoName}/<PACKAGE>`]
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
                                `registry=${location.origin}/${this.projectId}/${this.repoName}/`,
                                `always-auth=true`,
                                `//${location.origin}/${this.projectId}/${this.repoName}/:username=${this.userInfo.username}`,
                                `//${location.origin}/${this.projectId}/${this.repoName}/:password=<password>`,
                                `//${location.origin}/${this.projectId}/${this.repoName}/:email=<email>`
                            ]
                        },
                        {
                            subTitle: '2、设置 npm registry为当前制品库仓库，进入命令行根据用户凭证登录',
                            codeList: [
                                `npm registry ${location.origin}/${this.projectId}/${this.repoName}/`,
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
                            codeList: [`npm install <PACKAGE_NAME>`]
                        },
                        {
                            subTitle: '2、也可以通过指定registry的方式去拉取包，如下命令',
                            codeList: [`npm install <PACKAGE_NAME> --registry ${location.origin}/${this.projectId}/${this.repoName}/`]
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
                                `npm install ${this.packageName}@${this.version} --registry ${location.origin}/${this.projectId}/${this.repoName}/`
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
                                `   <url>${location.origin}/${this.projectId}/${this.repoName}/</url>`,
                                `   <mirrorOf>central</mirrorOf>`,
                                `</mirror>`
                            ]
                        },
                        {
                            subTitle: '2、项目设置，项目pom.xml中添加源地址',
                            codeList: [
                                `<repository>`,
                                `   <id>${this.projectId}-${this.repoName}</id>`,
                                `   <url>${location.origin}/${this.projectId}/${this.repoName}/</url>`,
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
                                `       <password>{password}</password>`,
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
                                `       <url>${location.origin}/${this.projectId}/${this.repoName}/</url>`,
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
                                `               <url>${location.origin}/${this.projectId}/${this.repoName}/</url>`,
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
