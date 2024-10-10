import { mapState, mapActions } from 'vuex'
const guideMap = {
    rds: 'helm'
}
export default {
    computed: {
        ...mapState(['userInfo', 'domain', 'dependAccessTokenValue', 'dependInputValue1', 'dependInputValue2', 'dependInputValue3']),
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
            // 包列表页不需要指定具体的包名，直接都指定为<PACKAGE_NAME>即可
            return this.$route.path.endsWith('/list') ? '<PACKAGE_NAME>' : this.packageKey.replace(/^.*:\/\/(?:.*:)*([^:]+)$/, '$1') || '<PACKAGE_NAME>'
        },
        versionLabel () {
            // 包列表页不需要指定具体的版本号，直接都指定为 <PACKAGE_VERSION> 即可
            return this.$route.path.endsWith('/list') ? '<PACKAGE_VERSION>' : this.version || '<PACKAGE_VERSION>'
        },
        repoUrl () {
            return `${location.origin}/${this.repoType}/${this.projectId}/${this.repoName}`
        },
        userName () {
            return this.userInfo.username || '<USERNAME>'
        },
        // docker manifest 需要修改版本之前的分隔符，带有sha256:的使用@符号，其他的使用:
        dockerSeparator () {
            // 注意，若用户手动在输入框的版本号输入框中输入了 版本号为sha256:xxxx，也需要修改版本之前的分隔符
            return ((this.repoType === 'docker' && this.dependInputValue2) || this.versionLabel).includes('sha256:') ? '@' : ':'
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
        packageFullPath () {
            return this.$route.query.packageFullPath || '/<RPM_FILE_NAME>'
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
                    inputBoxList: [
                        {
                            key: 'dependInputValue3', // vux中存储的变量名
                            label: this.$t('dockerImageTag'), // 输入框左侧label文案
                            placeholder: this.$t('pleaseInput') + this.$t('space') + this.$t('dockerImageTag'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE3' // vuex中mutations中的方法名
                        },
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('artifactName'), // 输入框左侧label文案
                            placeholder: this.$t('artifactNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('packageVersionPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('dockerPushGuideSubTitle1'),
                            codeList: [`docker tag ${this.dependInputValue3 || '<LOCAL_IMAGE_TAG>'} ${this.domain.docker}/${this.projectId}/${this.repoName}/${this.dependInputValue1 || this.packageName}:${this.dependInputValue2 || this.versionLabel}`]
                        },
                        {
                            subTitle: this.$t('dockerPushGuideSubTitle2'),
                            codeList: [`docker push ${this.domain.docker}/${this.projectId}/${this.repoName}/${this.dependInputValue1 || this.packageName}:${this.dependInputValue2 || this.versionLabel}`]
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('artifactName'), // 输入框左侧label文案
                            placeholder: this.$t('artifactNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('packageVersionPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('dockerDownloadGuideSubTitle'),
                            codeList: [`docker pull ${this.domain.docker}/${this.projectId}/${this.repoName}/${this.dependInputValue1 || this.packageName}${this.dockerSeparator}${this.dependInputValue2 || this.versionLabel}`]
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
                                `docker pull ${this.domain.docker}/${this.projectId}/${this.repoName}/${this.packageName}${this.dockerSeparator}${this.versionLabel}`
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
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('artifactName'), // 输入框左侧label文案
                            placeholder: this.$t('artifactNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('packageVersionPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('npmPushGuideSubTitle1'),
                            codeList: [
                                ' {',
                                `    "name": "${this.dependInputValue1 || '<PACKAGE_NAME>'}"`,
                                `    "version": "${this.dependInputValue2 || '<PACKAGE_VERSION>'}"`,
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
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('artifactName'), // 输入框左侧label文案
                            placeholder: this.$t('artifactNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('npmVersionInputPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('npmDownloadGuideSubTitle1'),
                            constructType: 'npm',
                            codeList: [`npm install ${this.dependInputValue1 || this.packageName + '@' + this.versionLabel}${this.dependInputValue1 && this.dependInputValue2 ? '@' + this.dependInputValue2 : ''}`]
                        },
                        {
                            subTitle: this.$t('npmDownloadGuideSubTitle1'),
                            constructType: 'yarn',
                            codeList: [`yarn add ${this.dependInputValue1 || this.packageName + '@' + this.versionLabel}${this.dependInputValue1 && this.dependInputValue2 ? '@' + this.dependInputValue2 : ''}`]
                        },
                        {
                            subTitle: this.$t('npmDownloadGuideSubTitle2'),
                            constructType: 'npm',
                            codeList: [`npm install ${this.dependInputValue1 || this.packageName + '@' + this.versionLabel}${this.dependInputValue1 && this.dependInputValue2 ? '@' + this.dependInputValue2 : ''} --registry ${this.domain.npm}/${this.projectId}/${this.repoName}/`]
                        },
                        {
                            subTitle: this.$t('npmDownloadGuideSubTitle2'),
                            constructType: 'yarn',
                            codeList: [`yarn add ${this.dependInputValue1 || this.packageName + '@' + this.versionLabel}${this.dependInputValue1 && this.dependInputValue2 ? '@' + this.dependInputValue2 : ''} --registry ${this.domain.npm}/${this.projectId}/${this.repoName}/`]
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
                    inputBoxList: [
                        // groupId
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('mavenGroupIdLabel'), // 输入框左侧label文案
                            placeholder: this.$t('mavenGroupIdPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        // artifactId
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('mavenArtifactIdLabel'), // 输入框左侧label文案
                            placeholder: this.$t('mavenArtifactIdPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        },
                        // 制品版本
                        {
                            key: 'dependInputValue3', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('packageVersionPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE3' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('mavenPushGuideSubTitle1'),
                            constructType: 'Apache Maven',
                            notShowArtifactInput: true, // Apache Maven不需要显示制品名称等输入框
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
                            notShowArtifactInput: true, // Apache Maven不需要显示制品名称等输入框
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
                                `            groupId = "${this.dependInputValue1 || '<GROUP_ID>'}"`,
                                `            artifactId = "${this.dependInputValue2 || '<ARTIFACT_ID>'}"`,
                                `            version = "${this.dependInputValue3 || '<PACKAGE_VERSION>'}"`,
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
                                `            groupId = "${this.dependInputValue1 || '<GROUP_ID>'}"`,
                                `            artifactId = "${this.dependInputValue2 || '<ARTIFACT_ID>'}"`,
                                `            version = "${this.dependInputValue3 || '<PACKAGE_VERSION>'}"`,
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
                    inputBoxList: [
                        // groupId
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('mavenGroupIdLabel'), // 输入框左侧label文案
                            placeholder: this.$t('mavenGroupIdPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        // artifactId
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('mavenArtifactIdLabel'), // 输入框左侧label文案
                            placeholder: this.$t('mavenArtifactIdPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        },
                        // 制品版本
                        {
                            key: 'dependInputValue3', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('packageVersionPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE3' // vuex中mutations中的方法名
                        }
                    ],
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
                                `        <groupId>${this.dependInputValue1 || '[GROUP_ID]'}</groupId>`,
                                `        <artifactId>${this.dependInputValue2 || '[ARTIFACT_ID]'}</artifactId>`,
                                `        <version>${this.dependInputValue3 || '[VERSION]'}</version>`,
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
                                `     api '${this.dependInputValue1 || '[GROUP_ID]'}:${this.dependInputValue2 || '[ARTIFACT_ID]'}:${this.dependInputValue3 || '[VERSION]'}'`,
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
                                `     api ("${this.dependInputValue1 || '[GROUP_ID]'}:${this.dependInputValue2 || '[ARTIFACT_ID]'}:${this.dependInputValue3 || '[VERSION]'}")`,
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
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('fileName'), // 输入框左侧label文案
                            placeholder: this.$t('fileNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('helmPushGuideSubTitle3'),
                            codeList: [
                                'helm package ./'
                            ]
                        },
                        {
                            subTitle: this.$t('helmPushGuideSubTitle1'),
                            codeList: [
                                `curl -F "chart=@${this.dependInputValue1 || '<FILE_NAME>'}" -u ${this.userName}:${this.accessToken} ${this.domain.helm}/api/${this.projectId}/${this.repoName}/charts`
                            ]
                        },
                        {
                            subTitle: this.$t('helmPushGuideSubTitle2'),
                            codeList: [
                                `curl -F "prov=@${this.dependInputValue1 || '<PROV_FILE_NAME>'}" -u ${this.userName}:${this.accessToken} ${this.domain.helm}/api/${this.projectId}/${this.repoName}/charts`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('artifactName'), // 输入框左侧label文案
                            placeholder: this.$t('artifactNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('packageVersionPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('helmPullGuideSubTitle'),
                            codeList: [
                                `helm fetch ${this.repoName}/${this.dependInputValue1 || this.packageName} --version ${this.dependInputValue2 || this.versionLabel}`
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
                                'helm repo update  '
                            ]
                        },
                        {
                            subTitle: this.$t('helmPullGuideSubTitle'),
                            codeList: [
                                `helm fetch ${this.repoName}/${this.packageName} --version ${this.versionLabel}`
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
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('fileName'), // 输入框左侧label文案
                            placeholder: this.$t('fileNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('pushGuideSubTitle'),
                            codeList: [
                                `curl -u ${this.userName}:${this.accessToken} -X PUT ${this.repoUrl}/ -T ${this.dependInputValue1 || '<RPM_FILE_NAME>'}`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('artifactName'), // 输入框左侧label文案
                            placeholder: this.$t('artifactNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('packageVersionPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('rpmPullGuideSunTitle1'),
                            codeList: [
                                `rpm -i ${location.protocol}//${this.userName}:${this.accessToken}@${location.host}/${this.repoType}/${this.projectId}/${this.dependInputValue1 || this.packageName}-${this.dependInputValue2 || this.versionLabel}.rpm`
                            ]
                        },
                        {
                            subTitle: this.$t('rpmPullGuideSunTitle2'),
                            codeList: [
                                `yum install ${this.dependInputValue1 || this.packageName}-${this.dependInputValue2 || this.versionLabel}`
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
                                `rpm -i ${location.protocol}//${this.userName}:<PERSONAL_ACCESS_TOKEN>@${location.host}/${this.repoType}/${this.projectId}/${this.repoName}${this.packageFullPath}`
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
                    title: this.$t('pypiCreditGuideSubTitle1'),
                    optionType: 'setCredentials',
                    main: [
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
                        }
                    ]
                },
                {
                    title: this.$t('pypiCreditGuideSubTitle3'),
                    optionType: 'setCredentials',
                    main: [
                        {
                            subTitle: this.$t('pypiCreditGuideSubTitle6'),
                            codeList: [
                                `pip3 config set global.index-url ${location.protocol}//${this.userName}:${this.accessToken}@${location.host}/${this.repoType}/${this.projectId}/${this.repoName}/simple`
                            ]
                        },
                        {
                            subTitle: this.$t('pypiCreditGuideSubTitle7'),
                            codeList: [
                                `pip3 config set install.trusted-host ${location.host}`
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
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('artifactName'), // 输入框左侧label文案
                            placeholder: this.$t('artifactNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('packageVersionPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('cmdPullGuideSubTitle'),
                            codeList: [
                                `pip3 install ${this.dependInputValue1 || this.packageName}==${this.dependInputValue2 || this.versionLabel}`
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
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('composerInputLabel'), // 输入框左侧label文案
                            placeholder: this.$t('composerInputPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('composerPushGuideSubTitle2')
                        },
                        {
                            subTitle: this.$t('composerPushGuideSubTitle'),
                            codeList: [
                                `curl -X PUT -u ${this.userName}:${this.accessToken} "${this.repoUrl}/" -T ${this.dependInputValue1 || '<PACKAGE_FILE>'}`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('artifactName'), // 输入框左侧label文案
                            placeholder: this.$t('artifactNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('packageVersionPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('cmdPullGuideSubTitle'),
                            codeList: [
                                `composer require ${this.dependInputValue1 || this.packageName} ${this.dependInputValue2 || this.versionLabel}`
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
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('artifactName'), // 输入框左侧label文案
                            placeholder: this.$t('artifactNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('nugetPushGuideSubTitle'),
                            codeList: [
                                `nuget push -Source "${this.repoName}" ${this.dependInputValue1 || '<LOCAL_PACKAGE_NAME>'}.nupkg`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('artifactName'), // 输入框左侧label文案
                            placeholder: this.$t('artifactNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('packageVersionPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('nugetPullGuideSubTitle'),
                            codeList: [
                                `nuget install -Source "${this.repoName}" -Version ${this.dependInputValue2 || this.versionLabel} ${this.dependInputValue1 || this.packageName}`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('delete'),
                    optionType: 'delete',
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('artifactName'), // 输入框左侧label文案
                            placeholder: this.$t('artifactNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('packageVersionPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('nugetDeleteGuideSubTitle'),
                            codeList: [
                                `nuget delete -Source "${this.repoName}" ${this.dependInputValue1 || this.packageName} ${this.dependInputValue2 || this.versionLabel}`
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
        conanGuide () {
            return [
                {
                    title: this.$t('setCredentials'),
                    optionType: 'setCredentials',
                    main: [
                        {
                            subTitle: this.$t('conanCreditGuideSubTitle1'),
                            codeList: [
                                `conan remote add ${this.repoName} ${this.domain.conan}/${this.projectId}/${this.repoName}`

                            ]
                        },
                        {
                            subTitle: this.$t('conanCreditGuideSubTitle2'),
                            codeList: [
                                `conan user add -p ${this.accessToken} -r ${this.repoName} ${this.userName}`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('push'),
                    optionType: 'push',
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('artifactName'), // 输入框左侧label文案
                            placeholder: this.$t('artifactNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('packageVersionPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('pushGuideSubTitle') + '(conan1.x)',
                            codeList: [
                                `conan upload ${this.dependInputValue1 || this.packageName}/${this.dependInputValue2 || this.versionLabel} -r ${this.repoName}`
                            ]
                        }
                    ]
                },
                {
                    title: this.$t('pull'),
                    optionType: 'pull',
                    inputBoxList: [
                        {
                            key: 'dependInputValue1', // vux中存储的变量名
                            label: this.$t('artifactName'), // 输入框左侧label文案
                            placeholder: this.$t('artifactNamePlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE1' // vuex中mutations中的方法名
                        },
                        {
                            key: 'dependInputValue2', // vux中存储的变量名
                            label: this.$t('artifactVersion'), // 输入框左侧label文案
                            placeholder: this.$t('packageVersionPlaceholder'), // 输入框提示文案
                            methodFunctionName: 'SET_DEPEND_INPUT_VALUE2' // vuex中mutations中的方法名
                        }
                    ],
                    main: [
                        {
                            subTitle: this.$t('helmPullGuideSubTitle'),
                            codeList: [
                                `conan install ${this.dependInputValue1 || this.packageName}/${this.dependInputValue2 || this.versionLabel}@ -r ${this.repoName}`
                            ]
                        }
                    ]
                }
            ]
        },
        conanInstall () {
            return [
                {
                    main: [
                        {
                            subTitle: this.$t('conanCreditGuideSubTitle1'),
                            codeList: [
                                `conan remote add ${this.repoName} ${this.domain.conan}/${this.projectId}/${this.repoName}`

                            ]
                        },
                        {
                            subTitle: this.$t('conanCreditGuideSubTitle2'),
                            codeList: [
                                `conan user add -p <PERSONAL_ACCESS_TOKEN> -r ${this.repoName} ${this.userName}`
                            ]
                        },
                        {
                            subTitle: this.$t('conanPullGuideSubTitle'),
                            codeList: [
                                `conan install ${this.packageName}/${this.versionLabel}@ -r ${this.repoName}`
                            ]
                        },
                        {
                            subTitle: this.$t('pushGuideSubTitle') + '(conan1.x)',
                            codeList: [
                                `conan upload ${this.packageName}/${this.versionLabel} -r ${this.repoName}`
                            ]
                        }
                    ]
                }
            ]
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
