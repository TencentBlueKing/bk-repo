<template>
    <canway-dialog
        v-model="show"
        width="810"
        height-num="700"
        :title="title"
        @cancel="cancel">
        <bk-form class="mr10 repo-base-info" :label-width="140" :model="repoBaseInfo" :rules="rules" ref="repoBaseInfo">
            <bk-form-item :label="$t('repoType')" :required="true" property="type" error-display-type="normal">
                <bk-radio-group v-model="repoBaseInfo.type" class="repo-type-radio-group" @change="changeRepoType">
                    <bk-radio-button v-for="repo in filterRepoEnum" :key="repo.label" :value="repo.value">
                        <div class="flex-column flex-center repo-type-radio">
                            <Icon size="32" :name="repo.value" />
                            <span>{{repo.label}}</span>
                        </div>
                    </bk-radio-button>
                </bk-radio-group>
            </bk-form-item>
            <bk-form-item :label="$t('repoName')" :required="true" property="name" error-display-type="normal">
                <bk-input class="w480" v-model.trim="repoBaseInfo.name" maxlength="32" show-word-limit
                    :placeholder="$t(repoBaseInfo.type === 'docker' ? 'repoDockerNamePlaceholder' : 'repoNamePlaceholder')">
                </bk-input>
                <div v-if="repoBaseInfo.type === 'docker'" class="form-tip">{{ $t('dockerRepoTip')}}</div>
            </bk-form-item>
            <template v-if="storeType === 'remote'">
                <bk-form-item :label="$t('address')" :required="true" property="url" error-display-type="normal">
                    <bk-input style="width:400px" v-model.trim="repoBaseInfo.url"></bk-input>
                    <bk-button theme="primary" :disabled="disableTestUrl" :loading="disableTestUrl" @click="onClickTestRemoteUrl">{{ $t('testRemoteUrl') }}</bk-button>
                </bk-form-item>
                <bk-form-item :label="$t('account')" property="credentials.username" error-display-type="normal">
                    <bk-input style="width:400px" v-model.trim="repoBaseInfo.credentials.username"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('password')" property="credentials.password" error-display-type="normal">
                    <bk-input style="width:400px" type="password" v-model.trim="repoBaseInfo.credentials.password"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('networkProxy')" property="switcher">
                    <template>
                        <bk-switcher v-model="repoBaseInfo.network.switcher" theme="primary"></bk-switcher>
                        <span>{{repoBaseInfo.network.switcher ? $t('open') : $t('close')}}</span>
                    </template>
                </bk-form-item>
                <template v-if="repoBaseInfo.network.switcher">
                    <bk-form-item label="IP" property="network.proxy.host" :required="true" error-display-type="normal">
                        <bk-input style="width:400px" v-model.trim="repoBaseInfo.network.proxy.host"></bk-input>
                    </bk-form-item>
                    <bk-form-item :label="$t('port')" property="network.proxy.port" :required="true" error-display-type="normal">
                        <bk-input style="width:400px" type="number" :max="65535" :min="1" v-model.trim="repoBaseInfo.network.proxy.port"></bk-input>
                    </bk-form-item>
                    <bk-form-item :label="$t('account')" property="network.proxy.username">
                        <bk-input style="width:400px" v-model.trim="repoBaseInfo.network.proxy.username"></bk-input>
                    </bk-form-item>
                    <bk-form-item :label="$t('password')" property="network.proxy.password">
                        <bk-input style="width:400px" type="password" v-model.trim="repoBaseInfo.network.proxy.password"></bk-input>
                    </bk-form-item>
                </template>
            </template>
            <template v-if="storeType === 'virtual'">
                <bk-form-item :label="$t('select') + $t('space') + $t('storageStore')" property="virtualStoreList" :required="true" error-display-type="normal">
                    <bk-button class="mb10" hover-theme="primary" @click="toCheckedStore">{{ $t('pleaseSelect') }}</bk-button>
                    <div class="virtual-check-container">
                        <store-sort
                            v-if="repoBaseInfo.virtualStoreList.length"
                            :key="repoBaseInfo.virtualStoreList"
                            ref="storeSortRef"
                            :sort-list="repoBaseInfo.virtualStoreList">
                        </store-sort>
                    </div>
                </bk-form-item>
                <bk-form-item :label="$t('uploadTargetStore')" property="uploadTargetStore">
                    <bk-select
                        v-model="repoBaseInfo.deploymentRepo"
                        style="width:300px;"
                        :show-empty="false"
                        :placeholder="$t('pleaseSelect') + $t('uploadTargetStore')">
                        <bk-option v-for="item in deploymentRepoCheckList" :key="item.name" :id="item.name" :name="item.name">
                        </bk-option>
                        <div v-if="!deploymentRepoCheckList.length" class="form-tip mt10 ml10 mr10 mb10">
                            {{$t('noAddedLocalStore')}}
                        </div>
                    </bk-select>
                    <div class="form-tip">{{$t('addPackagePrompt')}}</div>
                </bk-form-item>
            </template>
            <bk-form-item :label="$t('accessPermission')">
                <card-radio-group
                    v-model="available"
                    :list="availableList">
                </card-radio-group>
            </bk-form-item>
            <bk-form-item :label="$t('isDisplay')">
                <bk-radio-group v-model="repoBaseInfo.display">
                    <bk-radio class="mr20" :value="true">{{ $t('enable') }}</bk-radio>
                    <bk-radio :value="false">{{ $t('disable') }}</bk-radio>
                </bk-radio-group>
            </bk-form-item>
            <template v-if="repoBaseInfo.type === 'generic'">
                <bk-form-item v-for="type in genericInterceptorsList" :key="type"
                    :label="$t(`${type}Download`)" :property="`${type}.enable`">
                    <bk-radio-group v-model="repoBaseInfo[type].enable">
                        <bk-radio class="mr20" :value="true">{{ $t('enable') }}</bk-radio>
                        <bk-radio :value="false">{{ $t('disable') }}</bk-radio>
                    </bk-radio-group>
                    <template v-if="repoBaseInfo[type].enable && ['mobile', 'web'].includes(type)">
                        <bk-form-item :label="$t('fileName')" :label-width="80" class="mt10"
                            :property="`${type}.filename`" required error-display-type="normal">
                            <bk-input class="w250" v-model.trim="repoBaseInfo[type].filename"></bk-input>
                            <i class="bk-icon icon-info f14 ml5" v-bk-tooltips="$t('fileNameRule')"></i>
                        </bk-form-item>
                        <bk-form-item :label="$t('metadata')" :label-width="80"
                            :property="`${type}.metadata`" required error-display-type="normal">
                            <bk-input class="w250" v-model.trim="repoBaseInfo[type].metadata" :placeholder="$t('metadataRule')"></bk-input>
                            <a class="f12 ml5" href="https://docs.bkci.net/services/bkrepo/meta" target="__blank">{{ $t('viewMetadataDocument') }}</a>
                        </bk-form-item>
                    </template>
                    <template v-if="repoBaseInfo[type].enable && type === 'ip_segment'">
                        <bk-form-item :label="$t('IP')" :label-width="80" class="mt10"
                            :property="`${type}.ipSegment`" :required="!repoBaseInfo[type].officeNetwork" error-display-type="normal">
                            <bk-input class="w250 mr10" v-model.trim="repoBaseInfo[type].ipSegment" :placeholder="$t('ipPlaceholder')" :maxlength="4096"></bk-input>
                            <bk-checkbox v-model="repoBaseInfo[type].officeNetwork">{{ $t('office_networkDownload') }}</bk-checkbox>
                            <i class="bk-icon icon-info f14 ml5" v-bk-tooltips="$t('office_networkDownloadTips')"></i>
                        </bk-form-item>
                        <bk-form-item :label="$t('whiteUser')" :label-width="80"
                            :property="`${type}.whitelistUser`" error-display-type="normal">
                            <bk-input v-if="isCommunity" class="w250" v-model.trim="repoBaseInfo[type].whitelistUser" :placeholder="$t('whiteUserPlaceholder')"></bk-input>
                            <bk-member-selector v-else v-model="repoBaseInfo[type].whitelistUser" class="w250" :placeholder="$t('whiteUserPlaceholder')"></bk-member-selector>
                        </bk-form-item>
                    </template>
                </bk-form-item>
            </template>
            <template v-if="repoBaseInfo.type === 'rpm' && !(storeType === 'remote') && !(storeType === 'virtual')">
                <bk-form-item :label="$t('enabledFileLists')">
                    <bk-checkbox v-model="repoBaseInfo.enabledFileLists"></bk-checkbox>
                </bk-form-item>
                <bk-form-item :label="$t('repodataDepth')" property="repodataDepth" error-display-type="normal">
                    <bk-input class="w480" v-model.trim="repoBaseInfo.repodataDepth"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('groupXmlSet')" property="groupXmlSet" error-display-type="normal">
                    <bk-tag-input
                        class="w480"
                        :value="repoBaseInfo.groupXmlSet"
                        @change="(val) => {
                            repoBaseInfo.groupXmlSet = val.map(v => {
                                return v.replace(/^([^.]*)(\.xml)?$/, '$1.xml')
                            })
                        }"
                        :list="[]"
                        trigger="focus"
                        :clearable="false"
                        allow-create
                        has-delete-icon>
                    </bk-tag-input>
                </bk-form-item>
            </template>
            <bk-form-item :label="$t('description')">
                <bk-input type="textarea"
                    class="w480"
                    maxlength="200"
                    :rows="6"
                    v-model.trim="repoBaseInfo.description"
                    :placeholder="$t('repoDescriptionPlaceholder')">
                </bk-input>
            </bk-form-item>
        </bk-form>
        <template #footer>
            <bk-button @click="cancel">{{$t('cancel')}}</bk-button>
            <bk-button class="ml10" :loading="loading" theme="primary" @click="confirm">{{$t('confirm')}}</bk-button>
        </template>
        <check-target-store
            ref="checkTargetStoreRef"
            :repo-type="repoBaseInfo.type"
            :check-list="repoBaseInfo.virtualStoreList"
            @checkedTarget="onCheckedTargetStore">
        </check-target-store>
    </canway-dialog>
</template>
<script>
    import CardRadioGroup from '@repository/components/CardRadioGroup'
    import StoreSort from '@repository/components/StoreSort'
    import CheckTargetStore from '@repository/components/CheckTargetStore'
    import { repoEnum, repoSupportEnum } from '@repository/store/publicEnum'
    import { mapActions } from 'vuex'
    import { isEmpty } from 'lodash'
    const getRepoBaseInfo = () => {
        return {
            type: 'generic',
            category: 'LOCAL',
            name: '',
            public: false,
            system: false,
            enabledFileLists: false,
            repodataDepth: 0,
            interceptors: [],
            groupXmlSet: [],
            description: '',
            display: true,
            mobile: {
                enable: false,
                filename: '',
                metadata: ''
            },
            web: {
                enable: false,
                filename: '',
                metadata: ''
            },
            ip_segment: {
                enable: false,
                officeNetwork: false,
                ipSegment: '',
                whitelistUser: ''
            },
            // 远程仓库的地址下面的账号和密码
            credentials: {
                username: null,
                password: null
            },
            url: '', // 远程仓库的地址
            // 远程仓库的网络代理
            network: {
                switcher: false,
                proxy: {
                    host: null,
                    port: null,
                    username: null,
                    password: null
                }
            },
            // 虚拟仓库的选中的存储库列表
            virtualStoreList: [],
            deploymentRepo: '' // 虚拟仓库中选择存储的本地仓库
        }
    }

    export default {
        name: 'createRepo',
        components: { CardRadioGroup, CheckTargetStore, StoreSort },
        props: {
            // 当前仓库类型ID
            storeType: {
                type: String,
                default: 'local'
            }
        },
        data () {
            return {
                repoEnum,
                show: false,
                loading: false,
                repoBaseInfo: getRepoBaseInfo(),
                // 因为创建仓库时拆分为本地/远程/虚拟，远程仓库和虚拟仓库没有generic选项，所以需要重新组合
                filterRepoEnum: repoEnum,
                disableTestUrl: false
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            isCommunity () {
                // 是否为社区版
                return RELEASE_MODE === 'community'
            },
            genericInterceptorsList () {
                return this.isCommunity ? ['mobile', 'web'] : ['mobile', 'web', 'ip_segment']
            },
            rules () {
                const filenameRule = [
                    {
                        required: true,
                        message: this.$t('pleaseFileName'),
                        trigger: 'blur'
                    }
                ]
                const metadataRule = [
                    {
                        required: true,
                        message: this.$t('pleaseMetadata'),
                        trigger: 'blur'
                    },
                    {
                        regex: /^[^\s]+:[^\s]+/,
                        message: this.$t('metadataRule'),
                        trigger: 'blur'
                    }
                ]
                const ipSegmentRule = [
                    {
                        required: true,
                        message: this.$t('pleaseIpSegment'),
                        trigger: 'blur'
                    },
                    {
                        validator: function (val) {
                            const ipList = val.split(',')
                            return ipList.every(ip => {
                                if (!ip) return true
                                return ipList.every(ip => /(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])(\b\/([0-9]|[1-2][0-9]|3[0-2])\b)/.test(ip))
                            })
                        },
                        message: this.$t('ipSegmentRule'),
                        trigger: 'blur'
                    }
                ]
                // 远程仓库的 地址校验规则
                const urlRule = [
                    {
                        required: true,
                        message: this.$t('pleaseInput') + this.$t('address'),
                        trigger: 'blur'
                    },
                    {
                        validator: this.checkRemoteUrl,
                        message: this.$t('pleaseInput') + this.$t('legit') + this.$t('address'),
                        trigger: 'blur'
                    }
                ]
                // 远程仓库下代理的IP和端口的校验的校验规则
                const proxyHostRule = [
                    {
                        required: true,
                        message: this.$t('pleaseInput') + this.$t('networkProxy') + 'IP',
                        trigger: 'blur'
                    }
                ]
                const proxyPortRule = [
                    {
                        required: true,
                        message: this.$t('pleaseInput') + this.$t('networkProxy') + this.$t('port'),
                        trigger: 'blur'
                    }
                ]
                // 虚拟仓库下选择存储库的校验
                const checkStorageRule = [
                    {
                        required: true,
                        message: this.$t('noSelectStorageStore') + this.$t('create') + this.$t('virtualStore'),
                        trigger: 'blur'
                    }
                ]
                return {
                    type: [
                        {
                            required: true,
                            message: this.$t('pleaseSelect') + this.$t('repoType'),
                            trigger: 'blur'
                        }
                    ],
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('repoName'),
                            trigger: 'blur'
                        },
                        {
                            regex: this.repoBaseInfo.type === 'docker' ? /^[a-z][a-z0-9\-_]{1,31}$/ : /^[a-zA-Z][a-zA-Z0-9\-_]{1,31}$/,
                            message: this.$t(this.repoBaseInfo.type === 'docker' ? 'repoDockerNamePlaceholder' : 'repoNamePlaceholder'),
                            trigger: 'blur'
                        },
                        {
                            validator: this.asynCheckRepoName,
                            message: this.$t('repoName') + ' ' + this.$t('exist'),
                            trigger: 'blur'
                        }
                    ],
                    repodataDepth: [
                        {
                            regex: /^(0|[1-9][0-9]*)$/,
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('repodataDepth'),
                            trigger: 'blur'
                        }
                    ],
                    groupXmlSet: [
                        {
                            validator: arr => {
                                return arr.every(v => {
                                    return /\.xml$/.test(v)
                                })
                            },
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('groupXmlSet') + `(.xml${this.$t('type')})`,
                            trigger: 'change'
                        }
                    ],
                    'mobile.filename': filenameRule,
                    'mobile.metadata': metadataRule,
                    'web.filename': filenameRule,
                    'web.metadata': metadataRule,
                    'ip_segment.ipSegment': this.repoBaseInfo.ip_segment.officeNetwork ? {} : ipSegmentRule,
                    // 远程仓库才应该有地址的校验
                    url: this.storeType === 'remote' ? urlRule : {},
                    // 远程仓库且开启网络代理才应该设置代理的IP和端口的校验
                    'network.proxy.host': (this.storeType === 'remote' && this.repoBaseInfo.network.switcher) ? proxyHostRule : {},
                    'network.proxy.port': (this.storeType === 'remote' && this.repoBaseInfo.network.switcher) ? proxyPortRule : {},
                    // 虚拟仓库的选择存储库的校验
                    virtualStoreList: this.storeType === 'virtual' ? checkStorageRule : {}
                }
            },
            available: {
                get () {
                    if (this.repoBaseInfo.public) return 'public'
                    if (this.repoBaseInfo.system) return 'system'
                    return 'project'
                },
                set (val) {
                    this.repoBaseInfo.public = val === 'public'
                    this.repoBaseInfo.system = val === 'system'
                }
            },
            availableList () {
                return [
                    { label: this.$t('openProjectLabel'), value: 'project', tip: this.$t('openProjectTip') },
                    // { label: '系统内公开', value: 'system', tip: '系统内成员可以使用' },
                    { label: this.$t('openPublicLabel'), value: 'public', tip: this.$t('openPublicTip') }
                ]
            },
            // 弹窗标题
            title () {
                return this.$t('create') + this.$t('space') + this.$t(this.storeType + 'Store')
            },
            // 虚拟仓库中选择上传的目标仓库的下拉列表数据
            deploymentRepoCheckList () {
                return this.repoBaseInfo.virtualStoreList.filter(item => item.category === 'LOCAL')
            }
        },
        watch: {
            storeType: {
                handler (val) {
                    //  远程及虚拟仓库，目前只支持maven、npm、pypi、nuget四种仓库
                    this.filterRepoEnum = val === 'local' ? repoEnum : repoEnum.filter(item => repoSupportEnum.includes(item))
                    // 因为远程仓库和虚拟仓库没有generic类型且远程仓库支持的制品类型有限，所以需要将其重新赋默认值
                    this.repoBaseInfo.type = this.filterRepoEnum[0] || ''
                }
            },
            deploymentRepoCheckList: {
                handler (val) {
                    // 当选中的存储库中没有本地仓库或者当前选中的上传目标仓库不在被选中的存储库中时需要将当前选中的上传目标仓库重置为空
                    if (!val.length || !(val.map((item) => item.name).includes(this.repoBaseInfo.deploymentRepo))) {
                        this.repoBaseInfo.deploymentRepo = ''
                    }
                }
            },
            'repoBaseInfo.type': {
                // 当选择的仓库类型改变时需要将选择的存储库重置为空
                handler () {
                    this.repoBaseInfo.virtualStoreList = []
                }
            }
        },
        methods: {
            ...mapActions(['createRepo', 'checkRepoName', 'testRemoteUrl']),
            // 打开选择存储库弹窗
            toCheckedStore () {
                this.$refs.checkTargetStoreRef && (this.$refs.checkTargetStoreRef.show = true)
            },
            showDialogHandler () {
                this.show = true
                this.repoBaseInfo = getRepoBaseInfo()
                this.$refs.repoBaseInfo && this.$refs.repoBaseInfo.clearError()
            },
            cancel () {
                this.show = false
                this.$emit('close')
            },
            asynCheckRepoName () {
                return this.checkRepoName({
                    projectId: this.projectId,
                    name: this.repoBaseInfo.name
                }).then(res => !res)
            },
            changeRepoType () {
                if (this.repoBaseInfo.type === 'docker') this.repoBaseInfo.name = ''
                this.$refs.repoBaseInfo.clearError()
            },
            checkRemoteUrl (val) {
                const reg = /^https?:\/\/(([a-zA-Z0-9_-])+(\.)?)*(:\d+)?(\/((\.)?(\?)?=?&?[a-zA-Z0-9_-](\?)?)*)*$/
                return reg?.test(val)
            },
            // 创建远程仓库弹窗中测试远程链接
            onClickTestRemoteUrl () {
                if (!this.repoBaseInfo?.url || isEmpty(this.repoBaseInfo.url) || !this.checkRemoteUrl(this.repoBaseInfo?.url)) {
                    this.$bkMessage({
                        theme: 'warning',
                        limit: 3,
                        message: this.$t('pleaseInput') + this.$t('legit') + this.$t('address')
                    })
                } else {
                    const body = {
                        type: this.repoBaseInfo.type.toUpperCase(),
                        url: this.repoBaseInfo.url,
                        credentials: this.repoBaseInfo.credentials,
                        network: {
                            proxy: null
                        }
                    }
                    if (this.repoBaseInfo.network.switcher) {
                        body.network.proxy = this.repoBaseInfo.network.proxy
                    }
                    this.disableTestUrl = true
                    this.testRemoteUrl({ body }).then((res) => {
                        if (res.success) {
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('successConnectServer')
                            })
                        } else {
                            this.$bkMessage({
                                theme: 'error',
                                message: this.$t('connectFailed') + `: ${res.message}`
                            })
                        }
                    }).finally(() => {
                        this.disableTestUrl = false
                    })
                }
            },
            // 选中的存储库弹窗确认事件
            onCheckedTargetStore (list) {
                this.repoBaseInfo.virtualStoreList = list
            },
            async confirm () {
                await this.$refs.repoBaseInfo.validate()
                const interceptors = []
                if (this.repoBaseInfo.type === 'generic') {
                    ['mobile', 'web', 'ip_segment'].forEach(type => {
                        const { enable, filename, metadata, ipSegment, whitelistUser, officeNetwork } = this.repoBaseInfo[type]
                        if (['mobile', 'web'].includes(type)) {
                            enable && interceptors.push({
                                type: type.toUpperCase(),
                                rules: { filename, metadata }
                            })
                        } else {
                            enable && interceptors.push({
                                type: type.toUpperCase(),
                                rules: {
                                    ipSegment: ipSegment.split(','),
                                    whitelistUser: this.isCommunity ? whitelistUser.split(',') : whitelistUser,
                                    officeNetwork
                                }
                            })
                        }
                    })
                }
                this.loading = true
                const body = {
                    projectId: this.projectId,
                    type: this.repoBaseInfo.type.toUpperCase(),
                    name: this.repoBaseInfo.name,
                    public: this.repoBaseInfo.public,
                    display: this.repoBaseInfo.display,
                    description: this.repoBaseInfo.description,
                    category: this.storeType.toUpperCase() || 'COMPOSITE',
                    configuration: {
                        type: this.storeType || 'composite',
                        settings: {
                            system: this.repoBaseInfo.system,
                            interceptors: interceptors.length ? interceptors : undefined,
                            ...(
                                this.repoBaseInfo.type === 'rpm'
                                    ? {
                                        enabledFileLists: this.repoBaseInfo.enabledFileLists,
                                        repodataDepth: this.repoBaseInfo.repodataDepth,
                                        groupXmlSet: this.repoBaseInfo.groupXmlSet
                                    }
                                    : {}
                            )
                        }
                    }
                }
                // 远程仓库，此时需要添加 地址，账号密码和网络代理相关的配置
                if (this.storeType === 'remote') {
                    body.configuration.url = this.repoBaseInfo.url
                    body.configuration.credentials = this.repoBaseInfo.credentials
                    body.configuration.network = {
                        proxy: null
                    }
                    if (this.repoBaseInfo.network.switcher) {
                        body.configuration.network = {
                            proxy: this.repoBaseInfo.network.proxy
                        }
                    }
                }
                // 虚拟仓库需要添加存储库相关配置
                if (this.storeType === 'virtual') {
                    body.configuration.repositoryList = this.repoBaseInfo.virtualStoreList.map(item => {
                        return {
                            name: item.name,
                            category: item.category,
                            projectId: item.projectId
                        }
                    })
                    body.configuration.deploymentRepo = this.repoBaseInfo.deploymentRepo || ''
                }
                this.createRepo({ body }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('create') + this.$t('space') + this.$t('repository') + this.$t('space') + this.$t('success')
                    })
                    this.cancel()
                    this.$emit('refresh')
                }).finally(() => {
                    this.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.repo-base-info {
    max-height: 442px;
    overflow-y: auto;
    .repo-type-radio-group {
        display: grid;
        grid-template: auto / repeat(6, 80px);
        gap: 20px;
        ::v-deep .bk-form-radio-button {
            .bk-radio-button-text {
                height: auto;
                line-height: initial;
                padding: 0;
                border-radius: 2px;
            }
        }
        .repo-type-radio {
            position: relative;
            padding: 5px;
            width: 80px;
            height: 60px;
        }
    }
}

.virtual-check-container{
    width: 96%;
}
::v-deep .bk-button-hover.bk-primary{
    font-size: 12px;
}
</style>
