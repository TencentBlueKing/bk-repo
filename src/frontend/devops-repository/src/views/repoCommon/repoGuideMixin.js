import { mapState, mapActions } from 'vuex'
const guideMap = {
    rds: 'helm'
}
export default {
    computed: {
        ...mapState(['userInfo', 'domain', 'dependAccessTokenValue']),
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
            return this.$route.query.packageKey || ''
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
        // 获取当前仓库类型(本地、远程、组合、虚拟)
        storeType () {
            return this.$route.query.storeType || ''
        },
        // 是否是 软件源模式
        whetherSoftware () {
            return this.$route.path.startsWith('/software')
        },
        // 远程及虚拟仓库下，软件源模式下不显示某些操作
        noShowOption () {
            return this.storeType === 'remote' || this.storeType === 'virtual' || this.whetherSoftware
        },
        accessToken () {
            return this.dependAccessTokenValue || '<PERSONAL_ACCESS_TOKEN>'
        },
        dockerGuide () {
            return [
                {
                    title: this.$t('setCredentials'),
                    optionType: 'setCredentials',
                    main: [
                        {
                            subTitle: this.$t('dockerGuideSubTitle'),
                            codeList: [`docker login -u ${this.userName} -p ${this.accessToken} ${this.domain.docker}`]
                        }
                    ]
                },
                {
                    title: this.$t('push'),
                    optionType: 'push',
                    main: [
                        {
                            subTitle: this.$t('dockerPushGuideSubTitle1'),
                            codeList: [`docker tag <LOCAL_IMAGE_TAG> ${this.domain.docker}/${this.projectId}/${this.repoName}/${this.packageName}`]
                        },
                        {
                            subTitle: this.$t('dockerPushGuideSubTitle2'),
                            codeList: [`docker push ${this.domain.docker}/${this.projectId}/${this.repoName}/${this.packageName}`]
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    main: [
                        {
                            subTitle: this.$t('dockerDownloadGuideSubTitle'),
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
                            subTitle: this.$t('useSubTips'),
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
                    title: this.$t('npmCreditGuideSubTitle6'),
                    optionType: 'setCredentials',
                    main: [
                        {
                            subTitle: this.$t('npmCreditGuideSubTitle7'),
                            constructType: 'npm',
                            codeList: [
                                `npm config set registry ${this.domain.npm}/${this.projectId}/${this.repoName}/`
                            ]
                        },
                        {
                            subTitle: this.$t('npmCreditGuideSubTitle7'),
                            constructType: 'yarn',
                            codeList: [
                                `yarn config set registry=${this.domain.npm}/${this.projectId}/${this.repoName}/`
                            ]
                        },
                        {
                            subTitle: this.$t('npmCreditGuideSubTitle8'),
                            constructType: 'npm',
                            codeList: [
                                'npm login'
                            ]
                        },
                        {
                            subTitle: this.$t('npmCreditGuideSubTitle8'),
                            constructType: 'yarn',
                            codeList: [
                                'yarn login'
                            ]
                        }
                    ]

                },
                {
                    title: this.$t('npmCreditGuideSubTitle1'),
                    optionType: 'setCredentials',
                    main: [
                       
                        {
                            subTitle: this.$t('npmCreditGuideSubTitle3'),
                            codeList: [
                                'node -e "require(\'readline\') .createInterface({input:process.stdin,output:process.stdout,historySize:0}) .question(\'PAT> \',p => { b64=Buffer.from(p.trim()).toString(\'base64\');console.log(b64);process.exit(); })"'
                            ]
                        },
                        {
                            subTitle: this.$t('npmCreditGuideSubTitle4')
                        },
                        {
                            subTitle: this.$t('npmCreditGuideSubTitle2'),
                            codeList: [
                                `registry=${this.domain.npm}/${this.projectId}/${this.repoName}/`,
                                'always-auth=true',
                                `//${this.domain.npm.split('//')[1]}/${this.projectId}/${this.repoName}/:username=${this.userName}`,
                                `//${this.domain.npm.split('//')[1]}/${this.projectId}/${this.repoName}/:_password=<BASE64_ENCODE_PERSONAL_ACCESS_TOKEN>`,
                                `//${this.domain.npm.split('//')[1]}/${this.projectId}/${this.repoName}/:email=${this.userInfo.email || '<EMAIL>'}`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('push'),
                    optionType: 'push',
                    main: [
                        {
                            subTitle: this.$t('npmPushGuideSubTitle1'),
                            codeList: [
                                ' {',
                                '    "name": "<PACKAGE_NAME>}"',
                                '    "version": "<PACKAGE_VERSION>}"',
                                '    "description": ""',
                                '    "main": "index.js"',
                                '    "author": ""',
                                '    "license": "MIT"',
                                ' }'
                            ]
                        },
                        {
                            subTitle: this.$t('npmPushGuideSubTitle2'),
                            constructType: 'npm',
                            codeList: ['npm publish']
                        },
                        {
                            subTitle: this.$t('npmPushGuideSubTitle2'),
                            constructType: 'yarn',
                            codeList: ['yarn publish']
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    main: [
                        {
                            subTitle: this.$t('npmDownloadGuideSubTitle1'),
                            constructType: 'npm',
                            codeList: [`npm install ${this.packageName}@${this.versionLabel}`]
                        },
                        {
                            subTitle: this.$t('npmDownloadGuideSubTitle1'),
                            constructType: 'yarn',
                            codeList: [`yarn add ${this.packageName}@${this.versionLabel}`]
                        },
                        {
                            subTitle: this.$t('npmDownloadGuideSubTitle2'),
                            constructType: 'npm',
                            codeList: [`npm install ${this.packageName}@${this.versionLabel} --registry ${this.domain.npm}/${this.projectId}/${this.repoName}/`]
                        },
                        {
                            subTitle: this.$t('npmDownloadGuideSubTitle2'),
                            constructType: 'yarn',
                            codeList: [`yarn add ${this.packageName}@${this.versionLabel} --registry ${this.domain.npm}/${this.projectId}/${this.repoName}/`]
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
                            subTitle: this.$t('npmDownloadGuideSubTitle1'),
                            codeList: [
                                `npm install ${this.packageName}@${this.versionLabel}`
                            ]
                        },
                        {
                            subTitle: this.$t('npmDownloadGuideSubTitle2'),
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
                    title: this.$t('setCredentials'),
                    optionType: 'setCredentials',
                    main: [
                        {
                            subTitle: this.$t('mavenCreditGuideSubTitle1'),
                            constructType: 'Apache Maven',
                            codeList: [
                                '<servers>',
                                '       <server>',
                                `               <id>${this.projectId}-${this.repoName}</id>`,
                                `               <username>${this.userName}</username>`,
                                `               <password>${this.accessToken}</password>`,
                                '       </server>',
                                '</servers>'
                            ]
                        },
                        {
                            subTitle: this.$t('mavenCreditGuideSubTitle2'),
                            constructType: 'Gradle Groovy DSL',
                            codeList: [
                                `cpackUrl=${this.repoUrl}`,
                                `cpackUsername=${this.userName}`,
                                `cpackPassword=${this.accessToken}`
                            ]
                        },
                        {
                            subTitle: this.$t('mavenCreditGuideSubTitle2'),
                            constructType: 'Gradle Kotlin DSL',
                            codeList: [
                                `cpackUrl=${this.repoUrl}`,
                                `cpackUsername=${this.userName}`,
                                `cpackPassword=${this.accessToken}`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('push'),
                    optionType: 'push',
                    main: [
                        {
                            subTitle: this.$t('mavenPushGuideSubTitle1'),
                            constructType: 'Apache Maven',
                            codeList: [
                                '<distributionManagement>',
                                '       <repository>',
                                `               <!--${this.$t('mavenPushGuideCodeListAnnotate')}-->`,
                                `               <id>${this.projectId}-${this.repoName}</id>`,
                                `               <name>${this.repoName}</name>`,
                                `               <url>${this.repoUrl}/</url>`,
                                '       </repository>',
                                '</distributionManagement>'
                            ]
                        },
                        {
                            subTitle: this.$t('mavenPushGuideSubTitle2'),
                            constructType: 'Apache Maven',
                            codeList: [
                                'mvn clean deploy'
                            ]
                        },
                        {
                            subTitle: this.$t('mavenPushGuideSubTitle3'),
                            constructType: 'Gradle Groovy DSL',
                            codeList: [
                                'plugins {',
                                '    id "maven-publish"',
                                '}',
                                'publishing {',
                                '    publications {',
                                '        maven(MavenPublication) {',
                                '            groupId = "<GROUP_ID>"',
                                '            artifactId = "<ARTIFACT_ID>"',
                                '            version = "<PACKAGE_VERSION>"',
                                '            from components.java',
                                '        }',
                                '    }',
                                '    repositories {',
                                '        maven {',
                                '            url = "${cpackUrl}"',
                                '            credentials {',
                                '                username = "${cpackUsername}"',
                                '                password = "${cpackPassword}"',
                                '            }',
                                '        }',
                                '    }',
                                '}'
                            ]
                        },
                        {
                            subTitle: this.$t('mavenPushGuideSubTitle4'),
                            constructType: 'Gradle Groovy DSL',
                            codeList: [
                                'gradle publish'
                            ]
                        },
                        {
                            subTitle: this.$t('mavenPushGuideSubTitle5'),
                            constructType: 'Gradle Kotlin DSL',
                            codeList: [
                                'plugins {',
                                '    `maven-publish`',
                                '}',
                                'publishing {',
                                '    publications {',
                                '        create<MavenPublication>("maven") {',
                                '            groupId = "<GROUP_ID>"',
                                '            artifactId = "<ARTIFACT_ID>"',
                                '            version = "<PACKAGE_VERSION>"',
                                '            from(components["java"])',
                                '        }',
                                '    }',
                                '    repositories {',
                                '        maven {',
                                '            val cpackUrl: String by project',
                                '            val cpackUsername: String by project',
                                '            val cpackPassword: String by project',
                                '            url = uri(cpackUrl)',
                                '            credentials {',
                                '                username = cpackUsername',
                                '                password = cpackPassword',
                                '            }',
                                '        }',
                                '    }',
                                '}'
                            ]
                        },
                        {
                            subTitle: this.$t('mavenPushGuideSubTitle6'),
                            constructType: 'Gradle Kotlin DSL',
                            codeList: [
                                'gradle publish'
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    main: [
                        {
                            title: this.$t('mavenGuideTitle'),
                            subTitle: this.$t('mavenGuideSubTitle1'),
                            constructType: 'Apache Maven',
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
                            subTitle: this.$t('mavenPullGuideSubTitle1'),
                            constructType: 'Apache Maven',
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
                            title: this.$t('mavenGuideTitle2'),
                            subTitle: this.$t('mavenGuideSubTitle2'),
                            constructType: 'Apache Maven',
                            codeList: [
                                '<repositories>',
                                '    <repository>',
                                `       <!--${this.$t('mavenPushGuideCodeListAnnotate')}-->`,
                                `       <id>${this.projectId}-${this.repoName}</id>`,
                                `       <name>${this.repoName}</name>`,
                                `       <url>${this.repoUrl}/</url>`,
                                '    </repository>',
                                '</repositories>'
                            ]
                        },
                        {
                            title: this.$t('mavenGuideTitle3'),
                            subTitle: this.$t('mavenPullGuideSubTitle7'),
                            constructType: 'Apache Maven',
                            codeList: [
                                '<dependencies>',
                                '    <dependency>',
                                '        <groupId>[GROUP_ID]</groupId>',
                                '        <artifactId>[ARTIFACT_ID]</artifactId>',
                                '        <version>[VERSION]</version>',
                                '    </dependency>',
                                '</dependencies>'
                            ]
                        },
                        {
                            subTitle: this.$t('mavenPullGuideSubTitle2'),
                            constructType: 'Apache Maven',
                            codeList: [
                                'mvn clean package'
                            ]
                        },
                        {
                            subTitle: this.$t('mavenPullGuideSubTitle3'),
                            constructType: 'Gradle Groovy DSL',
                            codeList: [
                                'repositories {',
                                '    maven {',
                                '        url = "${cpackUrl}"',
                                '        credentials {',
                                '            username = "${cpackUsername}"',
                                '            password = "${cpackPassword}"',
                                '        }',
                                '    }',
                                '}',
                                '   ',
                                'dependencies { ',
                                '    api \'[GROUP_ID]:[ARTIFACT_ID]:[VERSION]\'',
                                '}'
                            ]
                        },
                        {
                            subTitle: this.$t('mavenPullGuideSubTitle4'),
                            constructType: 'Gradle Groovy DSL',
                            codeList: [
                                'gradle dependencies'
                            ]
                        },
                        {
                            subTitle: this.$t('mavenPullGuideSubTitle5'),
                            constructType: 'Gradle Kotlin DSL',
                            codeList: [
                                'repositories {',
                                '    maven {',
                                '        val cpackUrl: String by project',
                                '        val cpackUsername: String by project',
                                '        val cpackPassword: String by project',
                                '        url = uri(cpackUrl)',
                                '        credentials {',
                                '            username = cpackUsername',
                                '            password = cpackPassword',
                                '        }',
                                '    }',
                                '}',
                                '   ',
                                'dependencies { ',
                                '     api ("[GROUP_ID]:[ARTIFACT_ID]:[VERSION]")',
                                '}'
                            ]
                        },
                        {
                            subTitle: this.$t('mavenPullGuideSubTitle6'),
                            constructType: 'Gradle Kotlin DSL',
                            codeList: [
                                'gradle dependencies'
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
                                this.detail.basic.type && this.detail.basic.type !== 'jar' && `   <type>${this.detail.basic.type}</type>`,
                                '</dependency>'
                            ].filter(Boolean)
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
                    title: this.$t('setCredentials'),
                    optionType: 'setCredentials',
                    main: [
                        {
                            subTitle: this.$t('helmCreditGuideSubTitle1'),
                            codeList: [
                                `helm repo add --username ${this.userName} --password ${this.accessToken} ${this.repoName} "${this.domain.helm}/${this.projectId}/${this.repoName}/"`
                            ]
                        },
                        {
                            subTitle: this.$t('helmCreditGuideSubTitle2'),
                            codeList: [
                                'helm repo update'
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('push'),
                    optionType: 'push',
                    main: [
                        {
                            subTitle: this.$t('helmPushGuideSubTitle1'),
                            codeList: [
                                `curl -F "chart=@<FILE_NAME>" -u ${this.userName}:${this.accessToken} ${this.domain.helm}/api/${this.projectId}/${this.repoName}/charts`
                            ]
                        },
                        {
                            subTitle: this.$t('helmPushGuideSubTitle2'),
                            codeList: [
                                `curl -F "prov=@<PROV_FILE_NAME>" -u ${this.userName}:${this.accessToken} ${this.domain.helm}/api/${this.projectId}/${this.repoName}/charts`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    main: [
                        {
                            subTitle: this.$t('helmPullGuideSubTitle'),
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
                            subTitle: this.$t('helmInstallGuideSubTitle1'),
                            codeList: [
                                `helm repo add --username ${this.userName} --password <PERSONAL_ACCESS_TOKEN> ${this.repoName} "${this.domain.helm}/${this.projectId}/${this.repoName}/"`
                            ]
                        },
                        {
                            subTitle: this.$t('helmInstallGuideSubTitle2'),
                            codeList: [
                                'helm repo update'
                            ]
                        },
                        {
                            subTitle: this.$t('helmPullGuideSubTitle'),
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
                    title: this.$t('setCredentials'),
                    optionType: 'setCredentials',
                    main: [
                        {
                            subTitle: this.$t('rpmCreditGuideSubTitle', [this.repoName]),
                            codeList: [
                                `[${this.repoName}]`,
                                `name=${this.repoName}`,
                                `baseurl=${this.repoUrl}`,
                                `username=${this.userName}`,
                                `password=${this.accessToken}`,
                                'enabled=1',
                                'gpgcheck=0'
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('push'),
                    optionType: 'push',
                    main: [
                        {
                            subTitle: this.$t('pushGuideSubTitle'),
                            codeList: [
                                `curl -u ${this.userName}:${this.accessToken} -X PUT ${this.repoUrl}/ -T <RPM_FILE_NAME>`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    main: [
                        {
                            subTitle: this.$t('rpmPullGuideSunTitle1'),
                            codeList: [
                                `rpm -i ${location.protocol}//${this.userName}:${this.accessToken}@${location.host}/${this.repoType}/${this.projectId}/${this.repoName}/<RPM_FILE_NAME>`
                            ]
                        },
                        {
                            subTitle: this.$t('rpmPullGuideSunTitle2'),
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
                            subTitle: this.$t('rpmInstallGuideSubTitle')
                        },
                        {
                            subTitle: 'RPM',
                            codeList: [
                                `rpm -i ${location.protocol}//${this.userName}:<PERSONAL_ACCESS_TOKEN>@${location.host}/${this.repoType}/${this.projectId}/${this.repoName}/<RPM_FILE_NAME>`
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
                    title: this.$t('setCredentials'),
                    optionType: 'setCredentials',
                    main: [
                        {
                            subTitle: this.$t('pypiCreditGuideSubTitle1')
                        },
                        {
                            subTitle: this.$t('pypiCreditGuideSubTitle2'),
                            codeList: [
                                '[distutils]',
                                `index-servers = ${this.repoName}`,
                                `[${this.repoName}]`,
                                `repository: ${this.repoUrl}`,
                                `username: ${this.userName}`,
                                `password: ${this.accessToken}`
                            ]
                        },
                        {
                            subTitle: this.$t('pypiCreditGuideSubTitle3')
                        },
                        {
                            subTitle: this.$t('pypiCreditGuideSubTitle4'),
                            codeList: [
                                '[global]',
                                `index-url = ${location.protocol}//${this.userName}:${this.accessToken}@${location.host}/${this.repoType}/${this.projectId}/${this.repoName}/simple`
                            ]
                        },
                        {
                            subTitle: this.$t('pypiCreditGuideSubTitle5'),
                            codeList: [
                                '[global]',
                                `index-url = ${location.protocol}//${this.userName}:${this.accessToken}@${location.host}/${this.repoType}/${this.projectId}/${this.repoName}/simple`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('push'),
                    optionType: 'push',
                    main: [
                        {
                            subTitle: this.$t('pypiPushGuideSubTitle'),
                            codeList: [
                                `python3 -m twine upload -r ${this.repoName} dist/*`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    main: [
                        {
                            subTitle: this.$t('cmdPullGuideSubTitle'),
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
                            subTitle: this.$t('pypiInstallGuideSubTitle'),
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
                    title: this.$t('setCredentials'),
                    optionType: 'setCredentials',
                    main: [
                        {
                            subTitle: this.$t('composerCreditGuideSubTitle1'),
                            codeList: [
                                `composer config repo.packagist composer ${this.repoUrl}`
                            ]
                        },
                        {
                            subTitle: this.$t('composerCreditGuideSubTitle2'),
                            codeList: [
                                '{',
                                '       "http-basic": {',
                                `               "${location.host}": {`,
                                `                       "username": "${this.userName}",`,
                                `                       "password": "${this.accessToken}"`,
                                '               }',
                                '       }',
                                '}'
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('push'),
                    optionType: 'push',
                    main: [
                        {
                            subTitle: this.$t('composerPushGuideSubTitle'),
                            codeList: [
                                `curl -X PUT -u ${this.userName}:${this.accessToken} "${this.repoUrl}/" -T <PACKAGE_FILE>`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    main: [
                        {
                            subTitle: this.$t('cmdPullGuideSubTitle'),
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
                            subTitle: this.$t('useSubTips'),
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
                    title: this.$t('setCredentials'),
                    optionType: 'setCredentials',
                    main: [
                        {
                            subTitle: this.$t('nugetCreditGuideSubTitle'),
                            codeList: [`nuget sources Add -Username "${this.userName}" -Password "${this.accessToken}" -Name "${this.repoName}" -Source "${location.origin}/${this.repoType}/${this.projectId}/${this.repoName}/v3/index.json"`]
                        }
                    ]
                },
                {
                    title: this.$t('push'),
                    optionType: 'push',
                    main: [
                        {
                            subTitle: this.$t('nugetPushGuideSubTitle'),
                            codeList: [
                                `nuget push -Source "${this.repoName}" <LOCAL_PACKAGE_NAME>.nupkg`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    main: [
                        {
                            subTitle: this.$t('nugetPullGuideSubTitle'),
                            codeList: [
                                `nuget install -Source "${this.repoName}" -Version ${this.versionLabel} ${this.packageName}`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('delete'),
                    optionType: 'delete',
                    main: [
                        {
                            subTitle: this.$t('nugetDeleteGuideSubTitle'),
                            codeList: [
                                `nuget delete -Source "${this.repoName}" ${this.packageName} ${this.versionLabel}`
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
                            subTitle: this.$t('useSubTips'),
                            codeList: [
                                `nuget install -Source "${this.repoName}" -Version ${this.versionLabel} ${this.packageName}`
                            ]
                        }
                    ]
                }]
        },
        articleGuide () {
            return this[`${guideMap[this.repoType] || this.repoType}Guide`]
        },
        articleInstall () {
            return this[`${guideMap[this.repoType] || this.repoType}Install`]
        }
    },
    watch: {
        repoType: {
            handler (type) {
                type && this.getDomain(type)
            },
            immediate: true
        }
    },
    methods: {
        ...mapActions(['getDomain'])
    }
}
