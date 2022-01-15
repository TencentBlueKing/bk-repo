import { mapState, mapActions } from 'vuex'
export default {
    computed: {
        ...mapState(['userInfo', 'domain']),
        projectId () {
            return this.$route.params.projectId || ''
        },
        repoType () {
            return this.$route.params.repoType || ''
        },
        repoName () {
            return this.$route.query.repoName || ''
        },
        packageKey () {
            return this.$route.query.package || ''
        },
        version () {
            return this.$route.query.version || ''
        },
        packageName () {
            return this.packageKey.replace(/^.*:\/\/(?:.*:)*([^:]+)$/, '$1') || '<PACKAGE_NAME>'
        },
        versionLabel () {
            return this.version || '<PACKAGE_VERSION>'
        },
        repoUrl () {
            return `${location.origin}/${this.repoType}/${this.projectId}/${this.repoName}`
        },
        userName () {
            return this.userInfo.username || '<USERNAME>'
        },
        dockerGuide () {
            return [
                {
                    title: '设置凭证',
                    main: [
                        {
                            subTitle: '在命令行执行以下命令登陆仓库',
                            codeList: [`docker login -u ${this.userName} -p <PERSONAL_ACCESS_TOKEN> ${this.domain.docker}`]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            subTitle: '1、在命令行执行以下命令给本地镜像打标签',
                            codeList: [`docker tag <LOCAL_IMAGE_TAG> ${this.domain.docker}/${this.projectId}/${this.repoName}/${this.packageName}`]
                        },
                        {
                            subTitle: '2、在命令行执行以下命令进行推送',
                            codeList: [`docker push ${this.domain.docker}/${this.projectId}/${this.repoName}/${this.packageName}`]
                        }
                    ]
                },
                {
                    title: '下载',
                    main: [
                        {
                            subTitle: '在命令行执行以下命令进行拉取',
                            codeList: [`docker pull ${this.domain.docker}/${this.projectId}/${this.repoName}/${this.packageName}`]
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
                            subTitle: '使用如下命令去拉取制品',
                            codeList: [
                                `docker pull ${this.domain.docker}/${this.projectId}/${this.repoName}/${this.packageName}:${this.versionLabel}`
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
                            subTitle: '将下列配置添加到项目的 package.json 文件同一级目录下的 .npmrc 文件中',
                            codeList: [
                                `registry=${this.domain.npm}/${this.projectId}/${this.repoName}/`,
                                'always-auth=true',
                                `//${this.domain.npm.split('//')[1]}/${this.projectId}/${this.repoName}/:username=${this.userName}`,
                                `//${this.domain.npm.split('//')[1]}/${this.projectId}/${this.repoName}/:_password=<BASE64_ENCODE_PERSONAL_ACCESS_TOKEN>`,
                                `//${this.domain.npm.split('//')[1]}/${this.projectId}/${this.repoName}/:email=<EMAIL>`
                            ]
                        },
                        {
                            subTitle: '生成<BASE64_ENCODE_PERSONAL_ACCESS_TOKEN>'
                        },
                        {
                            subTitle: '1、在command/shell命令行窗口运行以下代码',
                            codeList: [
                                'node -e "require(\'readline\') .createInterface({input:process.stdin,output:process.stdout,historySize:0}) .question(\'PAT> \',p => { b64=Buffer.from(p.trim()).toString(\'base64\');console.log(b64);process.exit(); })"'
                            ]
                        },
                        {
                            subTitle: '2、复制<PERSONAL_ACCESS_TOKEN>至命令行窗口后，按下Enter键'
                        },
                        {
                            subTitle: '3、复制编码后的token，替换<BASE64_ENCODE_PERSONAL_ACCESS_TOKEN>'
                        },
                        {
                            subTitle: '方式二：使用交互式命令行设置凭证'
                        },
                        {
                            subTitle: '使用命令行窗口，设置 npm registry 为当前制品库仓库',
                            codeList: [
                                `npm config set registry ${this.domain.npm}/${this.projectId}/${this.repoName}/`
                            ]
                        },
                        {
                            subTitle: '进入命令行窗口根据用户凭证登录',
                            codeList: [
                                'npm login'
                            ]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            subTitle: '在命令行执行以下命令推送制品',
                            codeList: ['npm publish']
                        }
                    ]
                },
                {
                    title: '下载',
                    main: [
                        {
                            subTitle: '1、在设置仓库地址之后就可以使用如下命令去拉取制品',
                            codeList: [`npm install ${this.packageName}`]
                        },
                        {
                            subTitle: '2、也可以通过指定registry的方式去拉取制品，如下命令',
                            codeList: [`npm install ${this.packageName} --registry ${this.domain.npm}/${this.projectId}/${this.repoName}/`]
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
                            subTitle: '1、在设置仓库地址之后就可以使用如下命令去拉取制品',
                            codeList: [
                                `npm install ${this.packageName}@${this.versionLabel}`
                            ]
                        },
                        {
                            subTitle: '2、也可以通过指定registry的方式去拉取制品，如下命令',
                            codeList: [
                                `npm install ${this.packageName}@${this.versionLabel} --registry ${this.domain.npm}/${this.projectId}/${this.repoName}/`
                            ]
                        }
                    ]
                }
            ]
        },
        mavenGuide () {
            return [
                {
                    title: '设置凭证',
                    main: [
                        {
                            subTitle: '在配置文件 conf/settings.xml设置账户密码；项目内 settings.xml 也可以设置，高优先级',
                            codeList: [
                                '<servers>',
                                '       <server>',
                                `               <id>${this.projectId}-${this.repoName}</id>`,
                                `               <username>${this.userName}</username>`,
                                '               <password><PERSONAL_ACCESS_TOKEN></password>',
                                '       </server>',
                                '</servers>'
                            ]
                        }
                    ]
                },
                {
                    title: '配置依赖源下载地址',
                    main: [
                        {
                            subTitle: '1、全局配置，将下列配置添加到conf/setting.xml文件中',
                            codeList: [
                                '<mirror>',
                                `       <id>${this.projectId}-${this.repoName}</id>`,
                                `       <name>${this.repoName}</name>`,
                                `       <url>${this.repoUrl}/</url>`,
                                '       <mirrorOf>central</mirrorOf>',
                                '</mirror>'
                            ]
                        },
                        {
                            subTitle: '2、项目设置，将下列配置添加到项目的pom.xml文件中',
                            codeList: [
                                '<repository>',
                                `       <id>${this.projectId}-${this.repoName}</id>`,
                                `       <url>${this.repoUrl}/</url>`,
                                '</repository>'
                            ]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            subTitle: '将下列配置添加到 pom.xml 文件中',
                            codeList: [
                                '<distributionManagement>',
                                '       <repository>',
                                '               <!--id值与配置的server id 一致-->',
                                `               <id>${this.projectId}-${this.repoName}</id>`,
                                `               <name>${this.repoName}</name>`,
                                `               <url>${this.repoUrl}/</url>`,
                                '       </repository>',
                                '</distributionManagement>'
                            ]
                        },
                        {
                            subTitle: '在命令行执行以下命令推送制品',
                            codeList: [
                                'mvn deploy'
                            ]
                        }
                    ]
                },
                {
                    title: '拉取',
                    main: [
                        {
                            subTitle: '将下列配置添加到 conf/settings.xml 文件中',
                            codeList: [
                                '<profiles>',
                                '       <profile>',
                                '               <id>repository proxy</id>',
                                '               <activation>',
                                '                       <activeByDefault>true</activeByDefault>',
                                '               </activation>',
                                '               <repositories>',
                                '                       <repository>',
                                `                               <id>${this.projectId}-${this.repoName}</id>`,
                                `                               <name>${this.repoName}</name>`,
                                `                               <url>${this.repoUrl}/</url>`,
                                '                               <releases>',
                                '                                       <enabled>true</enabled>',
                                '                               </releases>',
                                '                               <snapshots>',
                                '                                       <enabled>true</enabled>',
                                '                               </snapshots>',
                                '                       </repository>',
                                '               </repositories>',
                                '       </profile>',
                                '</profiles>'
                            ]
                        },
                        {
                            subTitle: '在命令行执行以下命令拉取制品',
                            codeList: [
                                'mvn package'
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
                                '<dependency>',
                                `   <groupId>${this.detail.basic.groupId}</groupId>`,
                                `   <artifactId>${this.detail.basic.artifactId}</artifactId>`,
                                `   <version>${this.versionLabel}</version>`,
                                '</dependency>'
                            ]
                        },
                        {
                            subTitle: 'Gradle Groovy DSL',
                            codeList: [
                                `implementation '${this.detail.basic.groupId}:${this.detail.basic.artifactId}:${this.versionLabel}'`
                            ]
                        },
                        {
                            subTitle: 'Gradle Kotlin DSL',
                            codeList: [
                                `implementation("${this.detail.basic.groupId}:${this.detail.basic.artifactId}:${this.versionLabel}")`
                            ]
                        }
                    ]
                }
            ]
        },
        helmGuide () {
            return [
                {
                    title: '设置凭证',
                    main: [
                        {
                            subTitle: '1、在命令行执行以下命令配置制品仓库凭据',
                            codeList: [
                                `helm repo add --username ${this.userName} --password <PERSONAL_ACCESS_TOKEN> ${this.repoName} "${this.repoUrl}"`
                            ]
                        },
                        {
                            subTitle: '2、更新本地repo信息',
                            codeList: [
                                'helm repo update'
                            ]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            subTitle: '使用 cURL 命令推送Chart',
                            codeList: [
                                `curl -F "chart=@<FILE_NAME>" -u ${this.userName}:<PERSONAL_ACCESS_TOKEN> ${location.origin}/${this.repoType}/api/${this.projectId}/${this.repoName}/charts`
                            ]
                        },
                        {
                            subTitle: '使用 cURL 命令推送Chart Provenance',
                            codeList: [
                                `curl -F "prov=@<PROV_FILE_NAME>" -u ${this.userName}:<PERSONAL_ACCESS_TOKEN> ${location.origin}/${this.repoType}/api/${this.projectId}/${this.repoName}/charts`
                            ]
                        }
                    ]
                },
                {
                    title: '拉取',
                    main: [
                        {
                            subTitle: '3、拉取',
                            codeList: [
                                `helm install ${this.repoName}/${this.packageName}`
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
                                `helm repo add --username ${this.userName} --password <PERSONAL_ACCESS_TOKEN> ${this.repoName} "${this.repoUrl}"`
                            ]
                        },
                        {
                            subTitle: '2、更新本地的repo信息',
                            codeList: [
                                'helm repo update'
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
                            subTitle: `将下列配置添加到 /etc/yum.repos.d/${this.repoName}.repo 文件中`,
                            codeList: [
                                `[${this.repoName}]`,
                                `name=${this.repoName}`,
                                `baseurl=${this.repoUrl}`,
                                `username=${this.userName}`,
                                'password=<PERSONAL_ACCESS_TOKEN>',
                                'enabled=1',
                                'gpgcheck=0'
                            ]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            subTitle: '在命令行执行以下命令推送制品',
                            codeList: [
                                `curl -u ${this.userName}:<PERSONAL_ACCESS_TOKEN> -X PUT ${this.repoUrl}/ -T <RPM_FILE_NAME>`
                            ]
                        }
                    ]
                },
                {
                    title: '下载',
                    main: [
                        {
                            subTitle: '使用 rpm 命令拉取',
                            codeList: [
                                `rpm -i ${location.protocol}//${this.userName}:<PERSONAL_ACCESS_TOKEN>@${location.host}/${this.repoType}/${this.projectId}/${this.repoName}/<RPM_FILE_NAME>`
                            ]
                        },
                        {
                            subTitle: '使用 yum 命令拉取',
                            codeList: [
                                `yum install ${this.packageName}`
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
                            subTitle: '使用RPM或者yum方式拉取制品'
                        },
                        {
                            subTitle: 'RPM',
                            codeList: [
                                `rpm -i ${location.protocol}//${this.userName}:<PERSONAL_ACCESS_TOKEN>@${this.repoUrl}/<RPM_FILE_NAME>`
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
                    title: '设置凭证',
                    main: [
                        {
                            subTitle: '设置推送凭证'
                        },
                        {
                            subTitle: '将下列配置添加到 $HOME/.pypirc 文件中',
                            codeList: [
                                '[distutils]',
                                `index-servers = ${this.repoName}`,
                                `[${this.repoName}]`,
                                `repository: ${this.repoUrl}`,
                                `username: ${this.userName}`,
                                'password: <PERSONAL_ACCESS_TOKEN>'
                            ]
                        },
                        {
                            subTitle: '设置拉取凭证'
                        },
                        {
                            subTitle: 'MacOS / Linux系统：将配置添加到 $HOME/.pip/pip.conf 文件中',
                            codeList: [
                                '[global]',
                                `index-url = ${location.protocol}//${this.userName}:<PERSONAL_ACCESS_TOKEN>@${location.host}/${this.repoType}/${this.projectId}/${this.repoName}/simple`
                            ]
                        },
                        {
                            subTitle: 'Windows系统：将配置添加到 %HOME%/pip/pip.ini 文件中',
                            codeList: [
                                '[global]',
                                `index-url = ${location.protocol}//${this.userName}:<PERSONAL_ACCESS_TOKEN>@${location.host}/${this.repoType}/${this.projectId}/${this.repoName}/simple`
                            ]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            subTitle: '进入 Python 项目目录，在命令行执行以下命令进行推送',
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
                            subTitle: '在命令行执行以下命令进行拉取',
                            codeList: [
                                `pip3 install ${this.packageName}==${this.versionLabel}`
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
                            subTitle: '通过指定registry的方式去拉取制品',
                            codeList: [
                                `pip3 install -i ${this.repoUrl}/simple ${this.packageName}==${this.versionLabel}`
                            ]
                        }
                    ]
                }
            ]
        },
        composerGuide () {
            return [
                {
                    title: '设置凭证',
                    main: [
                        {
                            subTitle: '1、在 Composer 制品的文件目录，用命令行执行以下命令配置制品仓库凭据',
                            codeList: [
                                `composer config repo.packagist composer ${this.repoUrl}`
                            ]
                        },
                        {
                            subTitle: '2、 在 Composer 制品的文件目录添加 auth.json，配置仓库认证信息',
                            codeList: [
                                '{',
                                '       "http-basic": {',
                                `               "${location.host}": {`,
                                `                       "username": "${this.userName}",`,
                                '                       "password": "<PERSONAL_ACCESS_TOKEN>"',
                                '               }',
                                '       }',
                                '}'
                            ]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            subTitle: '使用 cURL 命令将压缩包上传至仓库',
                            codeList: [
                                `curl -X PUT -u ${this.userName}:<PERSONAL_ACCESS_TOKEN> "${this.repoUrl}/" -T <PACKAGE_FILE>`
                            ]
                        }
                    ]
                },
                {
                    title: '拉取',
                    main: [
                        {
                            subTitle: '在命令行执行以下命令进行拉取',
                            codeList: [
                                `composer require ${this.packageName} ${this.versionLabel}`
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
                            subTitle: '使用如下命令去拉取制品',
                            codeList: [
                                `composer require ${this.packageName} ${this.versionLabel}`
                            ]
                        }
                    ]
                }
            ]
        },
        gitGuide () {
            return []
        },
        gitInstall () {
            return []
        },
        nugetGuide () {
            return [
                {
                    title: '设置凭证',
                    main: [
                        {
                            subTitle: '在命令行执行以下命令配置制品仓库凭据：',
                            codeList: [`nuget sources Add -Username "${this.userName}" -Password "<PERSONAL_ACCESS_TOKEN>" -Name "${this.repoName}" -Source "${location.origin}/${this.repoType}/${this.projectId}/${this.repoName}/v3/index.json"`]
                        }
                    ]
                },
                {
                    title: '推送',
                    main: [
                        {
                            subTitle: '将<LOCAL_PACKAGE_NAME>替换为本地制品名称，命令行执行以下命令推送制品：',
                            codeList: [
                                `nuget push -ApiKey api -Source "${this.repoName}" <LOCAL_PACKAGE_NAME>.nupkg`
                            ]
                        }
                    ]
                },
                {
                    title: '拉取',
                    main: [
                        {
                            subTitle: '在命令行执行以下命令拉取制品：',
                            codeList: [
                                `nuget install -Source "${this.repoName}" -Version ${this.versionLabel} ${this.packageName}`
                            ]
                        }
                    ]
                },
                {
                    title: '删除',
                    main: [
                        {
                            subTitle: '注意：通过本操作删除的制品无法恢复',
                            codeList: [
                                `nuget delete -ApiKey api -Source "${this.repoName}" ${this.packageName} ${this.versionLabel}`
                            ]
                        }
                    ]
                }
            ]
        },
        nugetInstall () {
            return [
                {
                    main: [
                        {
                            subTitle: '使用如下命令去拉取制品',
                            codeList: [
                                `nuget install -Source "${this.repoName}" -Version ${this.versionLabel} ${this.packageName}`
                            ]
                        }
                    ]
                }]
        },
        articleGuide () {
            return this[`${this.$route.params.repoType}Guide`]
        },
        articleInstall () {
            return this[`${this.$route.params.repoType}Install`]
        }
    },
    created () {
        !this.domain.docker && this.getDockerDomain()
        !this.domain.npm && this.getNpmDomain()
    },
    methods: {
        ...mapActions(['getDockerDomain', 'getNpmDomain'])
    }
}
