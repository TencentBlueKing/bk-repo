<template>
    <div class="repo-config-container" v-bkloading="{ isLoading }">
        <bk-tab class="repo-config-tab" type="unborder-card" :active.sync="tabName">
            <bk-tab-panel name="baseInfo" :label="$t('repoBaseInfo')">
                <bk-form ref="repoBaseInfo" class="repo-base-info" :label-width="150" :model="repoBaseInfo" :rules="rules">
                    <bk-form-item :label="$t('repoName')">
                        <div class="flex-align-center">
                            <icon size="20" :name="repoBaseInfo.repoType || repoType" />
                            <span class="ml10">{{replaceRepoName(repoBaseInfo.name || repoName)}}</span>
                        </div>
                    </bk-form-item>
                    <bk-form-item :label="$t('repoAddress')">
                        <span>{{repoAddress}}</span>
                    </bk-form-item>
                    <bk-form-item label="访问权限">
                        <card-radio-group
                            v-model="available"
                            :list="availableList">
                        </card-radio-group>
                    </bk-form-item>
                    <template v-if="repoType === 'generic'">
                        <bk-form-item :label="$t('mobileDownload')" property="mobileDownload">
                            <bk-radio-group v-model="repoBaseInfo.mobileDownload">
                                <bk-radio class="mr20" :value="true">{{ $t('open') }}</bk-radio>
                                <bk-radio :value="false">{{ $t('close') }}</bk-radio>
                            </bk-radio-group>
                            <template v-if="repoBaseInfo.mobileDownload">
                                <bk-form-item :label="$t('fileName')" :label-width="60" class="mt10"
                                    property="interceptorsRulesMap.mobile.filename" error-display-type="normal">
                                    <bk-input class="w250" v-model.trim="repoBaseInfo.interceptorsRulesMap.mobile.filename"></bk-input>
                                </bk-form-item>
                                <bk-form-item :label="$t('metadata')" :label-width="60"
                                    property="interceptorsRulesMap.mobile.metadata" error-display-type="normal">
                                    <bk-input class="w250" v-model.trim="repoBaseInfo.interceptorsRulesMap.mobile.metadata"></bk-input>
                                </bk-form-item>
                            </template>
                        </bk-form-item>
                        <bk-form-item :label="$t('webDownload')" property="webDownload">
                            <bk-radio-group v-model="repoBaseInfo.webDownload">
                                <bk-radio class="mr20" :value="true">{{ $t('open') }}</bk-radio>
                                <bk-radio :value="false">{{ $t('close') }}</bk-radio>
                            </bk-radio-group>
                            <template v-if="repoBaseInfo.webDownload">
                                <bk-form-item :label="$t('fileName')" :label-width="60" class="mt10"
                                    property="interceptorsRulesMap.web.filename" error-display-type="normal">
                                    <bk-input class="w250" v-model.trim="repoBaseInfo.interceptorsRulesMap.web.filename"></bk-input>
                                </bk-form-item>
                                <bk-form-item :label="$t('metadata')" :label-width="60"
                                    property="interceptorsRulesMap.web.metadata" error-display-type="normal">
                                    <bk-input class="w250" v-model.trim="repoBaseInfo.interceptorsRulesMap.web.metadata"></bk-input>
                                </bk-form-item>
                            </template>
                        </bk-form-item>
                    </template>
                    <template v-if="repoType === 'rpm'">
                        <bk-form-item :label="$t('enabledFileLists')">
                            <bk-checkbox v-model="repoBaseInfo.enabledFileLists"></bk-checkbox>
                        </bk-form-item>
                        <bk-form-item :label="$t('repodataDepth')" property="repodataDepth" error-display-type="normal">
                            <bk-input v-model.trim="repoBaseInfo.repodataDepth"></bk-input>
                        </bk-form-item>
                        <bk-form-item :label="$t('groupXmlSet')" property="groupXmlSet" error-display-type="normal">
                            <bk-tag-input
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
                            :placeholder="$t('repoDescriptionPlacehodler')">
                        </bk-input>
                    </bk-form-item>
                    <bk-form-item>
                        <bk-button :loading="repoBaseInfo.loading" theme="primary" @click="saveBaseInfo">{{$t('save')}}</bk-button>
                    </bk-form-item>
                </bk-form>
            </bk-tab-panel>
            <bk-tab-panel v-if="showProxyConfigTab" name="proxyConfig" :label="$t('proxyConfig')">
                <proxy-config :base-data="repoBaseInfo" @refresh="getRepoInfoHandler"></proxy-config>
            </bk-tab-panel>
            <!-- <bk-tab-panel render-directive="if" name="permissionConfig" :label="$t('permissionConfig')">
                <permission-config></permission-config>
            </bk-tab-panel> -->
        </bk-tab>
    </div>
</template>
<script>
    import CardRadioGroup from '@repository/components/CardRadioGroup'
    import proxyConfig from '@repository/views/repoConfig/proxyConfig'
    // import permissionConfig from './permissionConfig'
    import { mapActions } from 'vuex'
    export default {
        name: 'repoConfig',
        components: { CardRadioGroup, proxyConfig },
        data () {
            return {
                tabName: 'baseInfo',
                isLoading: false,
                repoBaseInfo: {
                    loading: false,
                    repoName: '',
                    public: false,
                    system: false,
                    repoType: '',
                    enabledFileLists: false,
                    repodataDepth: 0,
                    groupXmlSet: [],
                    description: '',
                    mobileDownload: false,
                    webDownload: false,
                    interceptors: [],
                    interceptorsRulesMap: {
                        mobile: {
                            filename: '',
                            metadata: ''
                        },
                        web: {
                            filename: '',
                            metadata: ''
                        }
                    }
                },
                rules: {
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
                    'interceptorsRulesMap.mobile.filename': [
                        {
                            required: true,
                            message: this.$t('pleaseFileName'),
                            trigger: 'blur'
                        }
                    ],
                    'interceptorsRulesMap.mobile.metadata': [
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
                    ],
                    'interceptorsRulesMap.web.filename': [
                        {
                            required: true,
                            message: this.$t('pleaseFileName'),
                            trigger: 'blur'
                        }
                    ],
                    'interceptorsRulesMap.web.metadata': [
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
                }
            }
        },
        computed: {
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
                return ['maven', 'pypi', 'npm', 'composer', 'nuget'].includes(this.repoType)
            },
            repoAddress () {
                return location.origin + `/${this.repoBaseInfo.repoType}/${this.projectId}/${this.repoBaseInfo.name}/`
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
                    { label: '项目内公开', value: 'project', tip: '项目内成员可以使用' },
                    // { label: '系统内公开', value: 'system', tip: '系统内成员可以使用' },
                    { label: '可匿名下载', value: 'public', tip: '不鉴权，任意终端都可下载' }
                ]
            }
        },
        created () {
            if (!this.repoName || !this.repoType) this.toRepoList()
            this.getRepoInfoHandler()
        },
        methods: {
            ...mapActions(['getRepoInfo', 'updateRepoInfo']),
            toRepoList () {
                this.$router.push({
                    name: 'repoList'
                })
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
                        repoType: res.type.toLowerCase()
                    }
                    
                    const { interceptors } = res.configuration.settings
                    if (interceptors) {
                        interceptors.forEach(i => {
                            if (i.type === 'MOBILE') {
                                this.repoBaseInfo.mobileDownload = true
                                this.repoBaseInfo.interceptorsRulesMap.mobile.filename = i.rules.filename
                                this.repoBaseInfo.interceptorsRulesMap.mobile.metadata = i.rules.metadata
                            }
                            if (i.type === 'WEB') {
                                this.repoBaseInfo.webDownload = true
                                this.repoBaseInfo.interceptorsRulesMap.web.filename = i.rules.filename
                                this.repoBaseInfo.interceptorsRulesMap.web.metadata = i.rules.metadata
                            }
                        })
                    }
                }).finally(() => {
                    this.isLoading = false
                })
            },
            async saveBaseInfo () {
                ['generic', 'rpm'].includes(this.repoType) && await this.$refs.repoBaseInfo.validate()
                const { mobileDownload, webDownload, interceptorsRulesMap } = this.repoBaseInfo
                let { interceptors } = this.repoBaseInfo
                interceptors = []
                if (this.repoType === 'generic') {
                    for (const [key, val] of Object.entries(interceptorsRulesMap)) {
                        if ((key === 'mobile' && mobileDownload) || (key === 'web' && webDownload)) {
                            interceptors.push({
                                type: key.toLocaleUpperCase(),
                                rules: val
                            })
                        }
                    }
                }
                const body = {
                    public: this.repoBaseInfo.public,
                    description: this.repoBaseInfo.description,
                    configuration: {
                        ...this.repoBaseInfo.configuration,
                        settings: {
                            system: this.repoBaseInfo.system,
                            interceptors: this.repoType === 'generic' && interceptors.length ? interceptors : undefined,
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
                this.repoBaseInfo.loading = true
                this.updateRepoInfo({
                    projectId: this.projectId,
                    name: this.repoName,
                    body
                }).then(res => {
                    this.getRepoInfoHandler()
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('success')
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
            height: calc(100% - 42px);
            overflow-y: auto;
        }
        .repo-base-info {
            max-width: 800px;
        }
    }
}
</style>
