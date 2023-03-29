<template>
    <div class="repo-config-container" v-bkloading="{ isLoading }">
        <bk-tab class="repo-config-tab page-tab" type="unborder-card" :active.sync="tabName">
            <bk-tab-panel name="baseInfo" :label="$t('repoBaseInfo')">
                <bk-form ref="repoBaseInfo" class="repo-base-info" :label-width="150" :model="repoBaseInfo" :rules="rules">
                    <bk-form-item :label="$t('repoName')">
                        <div class="flex-align-center">
                            <icon size="20" :name="repoBaseInfo.repoType || repoType" />
                            <span class="ml10">{{replaceRepoName(repoBaseInfo.name || repoName)}}</span>
                        </div>
                    </bk-form-item>
                    <bk-form-item :label="$t('storeTypes')">
                        <div class="flex-align-center">
                            <icon size="20" :name="(repoBaseInfo.category && repoBaseInfo.category.toLowerCase() || 'local') + '-store'" />
                            <span class="ml10">{{$t((repoBaseInfo.category.toLowerCase() || 'local') + 'Store' ) }}</span>
                        </div>
                    </bk-form-item>
                    <bk-form-item :label="$t('repoAddress')">
                        <span>{{repoAddress}}</span>
                    </bk-form-item>
                    <template v-if="repoBaseInfo.category === 'REMOTE'">
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
                    <template v-if="repoBaseInfo.category === 'VIRTUAL'">
                        <bk-form-item :label=" $t('select') + $t('storageStore')" property="virtualStoreList" :required="true" error-display-type="normal">
                            <bk-button class="mb10" hover-theme="primary" @click="toCheckedStore">{{ $t('pleaseSelect') }}</bk-button>
                            <div class="virtual-check-container">
                                <store-sort
                                    v-if="repoBaseInfo.virtualStoreList.length"
                                    :key="repoBaseInfo.virtualStoreList"
                                    ref="storeSortRef"
                                    :sort-list="repoBaseInfo.virtualStoreList"
                                    @update="onUpdateList"></store-sort>
                            </div>
                        </bk-form-item>
                        <bk-form-item :label="$t('uploadTargetStore')" property="deploymentRepo">
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
                            <bk-radio class="mr20" :value="true">{{ $t('open') }}</bk-radio>
                            <bk-radio :value="false">{{ $t('close') }}</bk-radio>
                        </bk-radio-group>
                    </bk-form-item>
                    <template v-if="repoType === 'generic'">
                        <bk-form-item v-for="type in genericInterceptorsList" :key="type"
                            :label="$t(`${type}Download`)" :property="`${type}.enable`">
                            <bk-radio-group v-model="repoBaseInfo[type].enable">
                                <bk-radio class="mr20" :value="true">{{ $t('open') }}</bk-radio>
                                <bk-radio :value="false">{{ $t('close') }}</bk-radio>
                            </bk-radio-group>
                            <template v-if="repoBaseInfo[type].enable && ['mobile', 'web'].includes(type)">
                                <bk-form-item :label="$t('fileName')" :label-width="60" class="mt10"
                                    :property="`${type}.filename`" required error-display-type="normal">
                                    <bk-input class="w250" v-model.trim="repoBaseInfo[type].filename"></bk-input>
                                    <i class="bk-icon icon-info f14 ml5" v-bk-tooltips="$t('fileNameRule')"></i>
                                </bk-form-item>
                                <bk-form-item :label="$t('metadata')" :label-width="60"
                                    :property="`${type}.metadata`" required error-display-type="normal">
                                    <bk-input class="w250" v-model.trim="repoBaseInfo[type].metadata" :placeholder="$t('metadataRule')"></bk-input>
                                    <a class="f12 ml5" href="https://docs.bkci.net/services/bkrepo/meta" target="__blank">{{ $t('viewMetadataDocument') }}</a>
                                </bk-form-item>
                            </template>
                            <template v-if="repoBaseInfo[type].enable && type === 'ip_segment'">
                                <bk-form-item :label="$t('IP')" :label-width="150" class="mt10"
                                    :property="`${type}.ipSegment`" :required="!repoBaseInfo[type].officeNetwork" error-display-type="normal">
                                    <bk-input class="w250 mr10" v-model.trim="repoBaseInfo[type].ipSegment" :placeholder="$t('ipPlaceholder')" :maxlength="4096"></bk-input>
                                    <bk-checkbox v-model="repoBaseInfo[type].officeNetwork">{{ $t('office_networkDownload') }}</bk-checkbox>
                                    <i class="bk-icon icon-info f14 ml5" v-bk-tooltips="$t('office_networkDownloadTips')"></i>
                                </bk-form-item>
                                <bk-form-item :label="$t('whiteUser')" :label-width="150"
                                    :property="`${type}.whitelistUser`" error-display-type="normal">
                                    <bk-input v-if="isCommunity" class="w250" v-model.trim="repoBaseInfo[type].whitelistUser" :placeholder="$t('whiteUserPlaceholder')"></bk-input>
                                    <bk-member-selector v-else v-model="repoBaseInfo[type].whitelistUser" class="w250" :placeholder="$t('whiteUserPlaceholder')"></bk-member-selector>
                                </bk-form-item>
                            </template>
                        </bk-form-item>
                    </template>
                    <template v-if="repoType === 'rpm'">
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
                    <bk-form-item>
                        <bk-button :loading="repoBaseInfo.loading" theme="primary" @click="saveBaseInfo">{{$t('save')}}</bk-button>
                    </bk-form-item>
                </bk-form>
            </bk-tab-panel>
            <bk-tab-panel render-directive="if" v-if="showProxyConfigTab" name="proxyConfig" :label="$t('proxyConfig')">
                <proxy-config :base-data="repoBaseInfo" @refresh="getRepoInfoHandler"></proxy-config>
            </bk-tab-panel>
            <!-- <bk-tab-panel v-if="showCleanConfigTab" name="cleanConfig" label="清理设置">
                <clean-config :base-data="repoBaseInfo" @refresh="getRepoInfoHandler"></clean-config>
            </bk-tab-panel> -->
            <!-- <bk-tab-panel render-directive="if" name="permissionConfig" :label="$t('permissionConfig')">
                <permission-config></permission-config>
            </bk-tab-panel> -->
        </bk-tab>
        <check-target-store
            ref="checkTargetStoreRef"
            :repo-type="repoBaseInfo.type"
            :check-list="repoBaseInfo.virtualStoreList"
            @checkedTarget="onCheckedTargetStore">
        </check-target-store>
    </div>
</template>
<script>
    import CardRadioGroup from '@repository/components/CardRadioGroup'
    import proxyConfig from '@repository/views/repoConfig/proxyConfig'
    // import cleanConfig from '@repository/views/repoConfig/cleanConfig'
    // import permissionConfig from './permissionConfig'
    import CheckTargetStore from '@repository/components/CheckTargetStore'
    import StoreSort from '@repository/components/StoreSort'
    import { mapState, mapActions } from 'vuex'
    import { isEmpty } from 'lodash'
    export default {
        name: 'repoConfig',
        components: {
            CardRadioGroup,
            proxyConfig,
            // cleanConfig
            StoreSort,
            CheckTargetStore
        },
        data () {
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
                            return /(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])(\b\/([0-9]|[1-2][0-9]|3[0-2])\b)/.test(ip)
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
                    message: this.$t('noSelectStorageStore') + this.$t('save'),
                    trigger: 'blur'
                }
            ]
            return {
                tabName: 'baseInfo',
                isLoading: false,
                repoBaseInfo: {
                    loading: false,
                    repoName: '',
                    public: false,
                    system: false,
                    repoType: '',
                    category: '',
                    display: true,
                    enabledFileLists: false,
                    repodataDepth: 0,
                    groupXmlSet: [],
                    description: '',
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
                        proxy: {
                            host: null,
                            port: null,
                            username: null,
                            password: null
                        }
                    },
                    // 虚拟仓库的选中的存储库列表
                    virtualStoreList: [],
                    deploymentRepo: '', // 虚拟仓库中选择存储的本地仓库
                    // 是否展示tab标签页，因为代理设置和清理设置需要根据详情页接口返回的数据判断是否显示，解决异步导致的tab顺序错误的问题
                    showTabPanel: false
                },
                disableTestUrl: false,
                filenameRule,
                metadataRule,
                ipSegmentRule,
                urlRule,
                proxyHostRule,
                proxyPortRule,
                checkStorageRule
            }
        },
        computed: {
            ...mapState(['domain']),
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            },
            repoType () {
                return this.$route.params.repoType
            },
            showProxyConfigTab () {
                return this.repoBaseInfo.category === 'COMPOSITE' && ['maven', 'pypi', 'npm', 'composer', 'nuget'].includes(this.repoType)
            },
            // showCleanConfigTab () {
            //     return ['maven', 'docker', 'npm', 'helm', 'generic'].includes(this.repoType)
            // },
            repoAddress () {
                const { repoType, name } = this.repoBaseInfo
                if (repoType === 'docker') {
                    return `${location.protocol}//${this.domain.docker}/${this.projectId}/${name}/`
                }
                return `${location.origin}/${repoType}/${this.projectId}/${name}/`
            },
            isCommunity () {
                return RELEASE_MODE === 'community'
            },
            genericInterceptorsList () {
                return this.isCommunity ? ['mobile', 'web'] : ['mobile', 'web', 'ip_segment']
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
            rules () {
                return {
                    repodataDepth: [
                        {
                            regex: /^(0|[1-9][0-9]*)$/,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('legit') + this.$t('space') + this.$t('repodataDepth'),
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
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('legit') + this.$t('space') + this.$t('groupXmlSet') + this.$t('space') + `(.xml${this.$t('type')})`,
                            trigger: 'change'
                        }
                    ],
                    'mobile.filename': this.filenameRule,
                    'mobile.metadata': this.metadataRule,
                    'web.filename': this.filenameRule,
                    'web.metadata': this.metadataRule,
                    'ip_segment.ipSegment': this.repoBaseInfo.ip_segment.officeNetwork ? {} : this.ipSegmentRule,
                    // 远程仓库才应该有地址的校验
                    url: this.repoBaseInfo.category === 'REMOTE' ? this.urlRule : {},
                    // 远程仓库且开启网络代理才应该设置代理的IP和端口的校验
                    'network.proxy.host': (this.repoBaseInfo.category === 'REMOTE' && this.repoBaseInfo.network.switcher) ? this.proxyHostRule : {},
                    'network.proxy.port': (this.repoBaseInfo.category === 'REMOTE' && this.repoBaseInfo.network.switcher) ? this.proxyPortRule : {},
                    // 虚拟仓库的选择存储库的校验
                    virtualStoreList: this.repoBaseInfo.category === 'VIRTUAL' ? this.checkStorageRule : {}
                }
            },
            // 虚拟仓库中选择上传的目标仓库的下拉列表数据
            deploymentRepoCheckList () {
                return this.repoBaseInfo.virtualStoreList.filter(item => item.category === 'LOCAL')
            }
        },
        watch: {
            repoType: {
                handler (type) {
                    type && this.getDomain(type)
                },
                immediate: true
            },
            deploymentRepoCheckList: {
                handler (val) {
                    // 当选中的存储库中没有本地仓库或者当前选中的上传目标仓库不在被选中的存储库中时需要将当前选中的上传目标仓库重置为空
                    if (!val.length || !(val.map((item) => item.name).includes(this.repoBaseInfo.deploymentRepo))) {
                        this.repoBaseInfo.deploymentRepo = ''
                    }
                }
            }
        },
        created () {
            if (!this.repoName || !this.repoType) this.toRepoList()
            this.getRepoInfoHandler()
        },
        methods: {
            ...mapActions(['getRepoInfo', 'updateRepoInfo', 'getDomain', 'testRemoteUrl']),
            // 打开选择存储库弹窗
            toCheckedStore () {
                this.$refs.checkTargetStoreRef && (this.$refs.checkTargetStoreRef.show = true)
            },
            // 当删除了选中的存储库时
            onUpdateList (list) {
                this.repoBaseInfo.virtualStoreList = list
            },
            // 选中的存储库弹窗确认事件
            onCheckedTargetStore (list) {
                this.repoBaseInfo.virtualStoreList = list
            },
            toRepoList () {
                this.$router.push({
                    name: 'repoList'
                })
            },
            checkRemoteUrl (val) {
                const reg = /^https?:\/\/(([a-zA-Z0-9_-])+(\.)?)*(:\d+)?(\/((\.)?(\?)?=?&?[a-zA-Z0-9_-](\?)?)*)*$/
                return reg.test(val)
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
            getRepoInfoHandler () {
                this.isLoading = true
                this.getRepoInfo({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    repoType: this.repoType
                }).then(res => {
                    this.repoBaseInfo = {
                        ...this.repoBaseInfo,
                        ...res,
                        ...res.configuration.settings,
                        repoType: res.type.toLowerCase(),
                        category: res.category
                    }
                    // 虚拟仓库，添加可选仓库穿梭框及上传目标仓库下拉框
                    if (res.category === 'VIRTUAL') {
                        this.repoBaseInfo.virtualStoreList = res.configuration.repositoryList
                        // 当后台返回的字段为null时需要将其设置为空字符串，否则会因为组件需要的参数类型不对应，导致选择框的placeholder不显示
                        this.repoBaseInfo.deploymentRepo = res.configuration.deploymentRepo || ''
                    }
                    // 远程仓库，添加地址，账号密码和网络代理相关配置
                    if (res.category === 'REMOTE') {
                        this.repoBaseInfo.url = res.configuration.url
                        this.repoBaseInfo.credentials = res.configuration.credentials
                        if (res.configuration.network.proxy === null) {
                            this.repoBaseInfo.network = {
                                proxy: {
                                    host: null,
                                    port: null,
                                    username: null,
                                    password: null
                                },
                                switcher: false
                            }
                        } else {
                            this.repoBaseInfo.network = {
                                proxy: res.configuration.network.proxy,
                                switcher: true
                            }
                        }
                    }

                    const { interceptors } = res.configuration.settings
                    if (interceptors instanceof Array) {
                        interceptors.forEach(i => {
                            if (i.type === 'IP_SEGMENT') {
                                const curRules = {
                                    ipSegment: i.rules.ipSegment.join(','),
                                    whitelistUser: this.isCommunity ? i.rules.whitelistUser.join(',') : i.rules.whitelistUser,
                                    officeNetwork: i.rules.officeNetwork
                                }
                                this.repoBaseInfo[i.type.toLowerCase()] = {
                                    enable: true,
                                    ...curRules
                                }
                            } else {
                                this.repoBaseInfo[i.type.toLowerCase()] = {
                                    enable: true,
                                    ...i.rules
                                }
                            }
                        })
                    }
                }).finally(() => {
                    this.isLoading = false
                    // 不论接口返回数据是否成功，都需要显示tab标签页
                    this.showTabPanel = true
                })
            },
            async saveBaseInfo () {
                await this.$refs.repoBaseInfo.validate()
                const interceptors = []
                if (this.repoType === 'generic') {
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
                const body = {
                    public: this.repoBaseInfo.public,
                    description: this.repoBaseInfo.description,
                    display: this.repoBaseInfo.display,
                    configuration: {
                        ...this.repoBaseInfo.configuration,
                        settings: {
                            system: this.repoBaseInfo.system,
                            interceptors: interceptors.length ? interceptors : undefined,
                            ...(
                                this.repoType === 'rpm'
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
                if (this.repoBaseInfo.category === 'REMOTE') {
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
                if (this.repoBaseInfo.category === 'VIRTUAL') {
                    body.configuration.repositoryList = this.repoBaseInfo.virtualStoreList.map(item => {
                        return {
                            name: item.name,
                            category: item.category
                        }
                    })
                    body.configuration.deploymentRepo = this.repoBaseInfo.deploymentRepo
                }
                this.repoBaseInfo.loading = true
                this.updateRepoInfo({
                    projectId: this.projectId,
                    name: this.repoName,
                    body
                }).then(() => {
                    this.getRepoInfoHandler()
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('space') + this.$t('success')
                    })
                }).finally(() => {
                    this.repoBaseInfo.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.repo-config-container {
    height: 100%;
    background-color: white;
    .repo-config-tab {
        height: 100%;
        ::v-deep .bk-tab-section {
            height: calc(100% - 60px);
            overflow-y: auto;
        }
        .repo-base-info {
            max-width: 800px;
        }
    }
}
.card-radio-group ::v-deep.card-radio{
    width: 274px;
}
</style>
