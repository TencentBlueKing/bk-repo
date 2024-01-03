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
                    <bk-form-item :label="$t('repoAddress')">
                        <span>{{repoAddress}}</span>
                    </bk-form-item>
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
                    <bk-form-item
                        :label="$t('bkPermissionCheck')"
                        v-if="!specialRepoEnum.includes(repoBaseInfo.name) && rbacStatus">
                        <bk-radio-group v-model="repoBaseInfo.configuration.settings.bkiamv3Check">
                            <bk-radio class="mr20" :value="true">{{ $t('open') }}</bk-radio>
                            <bk-radio :value="false">{{ $t('close') }}</bk-radio>
                        </bk-radio-group>
                    </bk-form-item>
                    <template v-if="repoType === 'generic'">
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
                                <bk-form-item :label="$t('IP')" :label-width="150" class="mt10"
                                    :property="`${type}.ipSegment`" :required="!repoBaseInfo[type].officeNetwork" error-display-type="normal">
                                    <bk-input class="w250 mr10" v-model.trim="repoBaseInfo[type].ipSegment" :placeholder="$t('ipPlaceholder')" :maxlength="4096"></bk-input>
                                    <bk-checkbox v-model="repoBaseInfo[type].officeNetwork">{{ $t('office_networkDownload') }}</bk-checkbox>
                                    <i class="bk-icon icon-info f14 ml5" v-bk-tooltips="$t('office_networkDownloadTips')"></i>
                                </bk-form-item>
                                <bk-form-item :label="$t('whiteUser')" :label-width="150"
                                    :property="`${type}.whitelistUser`" error-display-type="normal">
                                    <bk-input v-if="isCommunity" class="w250" v-model.trim="repoBaseInfo[type].whitelistUser" :placeholder="$t('whiteUserPlaceholder')"></bk-input>
                                    <bk-member-selector v-else v-model="repoBaseInfo[type].whitelistUser" class="member-selector" :placeholder="$t('whiteUserPlaceholder')"></bk-member-selector>
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
            <bk-tab-panel v-if="showCleanConfigTab" name="cleanConfig" :label="$t('cleanConfig')">
                <clean-config :base-data="repoBaseInfo" @refresh="getRepoInfoHandler"></clean-config>
            </bk-tab-panel>
            <bk-tab-panel render-directive="if" v-if="showPermissionConfigTab" name="permissionConfig" :label="$t('permissionConfig')">
                <permission-config :base-data="repoBaseInfo" @refresh="getRepoInfoHandler"></permission-config>
            </bk-tab-panel>
        </bk-tab>
        <iam-deny-dialog :visible.sync="showIamDenyDialog" :show-data="showData"></iam-deny-dialog>
    </div>
</template>
<script>
    import CardRadioGroup from '@repository/components/CardRadioGroup'
    import proxyConfig from '@repository/views/repoConfig/proxyConfig'
    import iamDenyDialog from '@repository/components/IamDenyDialog/IamDenyDialog'
    import permissionConfig from './permissionConfig/permissionConfig'
    import cleanConfig from '@repository/views/repoConfig/cleanConfig'
    import { mapState, mapActions } from 'vuex'
    import { specialRepoEnum } from '@repository/store/publicEnum'
    export default {
        name: 'repoConfig',
        components: {
            CardRadioGroup,
            proxyConfig,
            iamDenyDialog,
            permissionConfig,
            cleanConfig
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
            return {
                specialRepoEnum,
                tabName: 'baseInfo',
                isLoading: false,
                repoBaseInfo: {
                    loading: false,
                    repoName: '',
                    public: false,
                    system: false,
                    repoType: '',
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
                    configuration: {
                        settings: {
                            bkiamv3Check: false
                        }
                    }
                },
                filenameRule,
                metadataRule,
                ipSegmentRule,
                showIamDenyDialog: false,
                showData: {},
                rbacStatus: false
            }
        },
        computed: {
            ...mapState(['domain', 'userInfo']),
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
            showCleanConfigTab () {
                return ['docker', 'generic', 'helm'].includes(this.repoType)
            },
            showPermissionConfigTab () {
                return ['generic'].includes(this.repoType) && (this.userInfo.admin || this.userInfo.manage) && !(['pipeline', 'custom'].includes(this.repoName))
            },
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
                    'ip_segment.ipSegment': this.repoBaseInfo.ip_segment.officeNetwork ? {} : this.ipSegmentRule
                }
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
        created () {
            if (!this.repoName || !this.repoType) this.toRepoList()
            this.getRepoInfoHandler()
            this.getIamPermissionStatus().then(res => {
                this.rbacStatus = res
            })
        },
        methods: {
            ...mapActions(['getRepoInfo', 'updateRepoInfo', 'getDomain', 'getPermissionUrl', 'getIamPermissionStatus']),
            toRepoList () {
                this.$router.push({
                    name: 'repositories'
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
                }).catch(err => {
                    if (err.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'READ',
                                resourceType: 'REPO',
                                uid: this.userInfo.name,
                                repoName: this.repoName
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'READ',
                                    url: res
                                }
                            }
                        })
                    }
                }).finally(() => {
                    this.isLoading = false
                })
            },
            async saveBaseInfo () {
                ['generic', 'rpm'].includes(this.repoType) && await this.$refs.repoBaseInfo.validate()
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
                if (!specialRepoEnum.includes(this.repoBaseInfo.name)) {
                    body.configuration.settings.bkiamv3Check = this.repoBaseInfo.configuration.settings.bkiamv3Check
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
                }).catch(err => {
                    if (err.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'MANAGE',
                                resourceType: 'REPO',
                                uid: this.userInfo.name,
                                repoName: this.repoName
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'MANAGE',
                                    url: res
                                }
                            } else {
                                this.$bkMessage({
                                    theme: 'error',
                                    message: err.message
                                })
                            }
                        })
                    } else {
                        this.$bkMessage({
                            theme: 'error',
                            message: err.message
                        })
                    }
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
            .member-selector{
                ::v-deep.bk-tag-selector .bk-tag-input {
                    height: auto;
                }
                width: 250px;
            }
        }
    }
}
</style>
