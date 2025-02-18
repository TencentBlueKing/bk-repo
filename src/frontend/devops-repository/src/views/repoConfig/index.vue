<template>
    <div class="repo-config-container" v-bkloading="{ isLoading }">
        <bk-tab class="repo-config-tab page-tab" type="unborder-card" :active.sync="tabName" ref="tab">
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
                    <bk-form-item :label="$t('isDisplay')">
                        <bk-radio-group v-model="repoBaseInfo.display">
                            <bk-radio class="mr20" :value="true">{{ $t('enable') }}</bk-radio>
                            <bk-radio :value="false">{{ $t('disable') }}</bk-radio>
                        </bk-radio-group>
                    </bk-form-item>
                    <bk-form-item
                        :label="$t('bkPermissionCheck')"
                        v-if="!specialRepoEnum.includes(repoBaseInfo.name)">
                        <bk-radio-group v-model="bkiamv3Check">
                            <bk-radio class="mr20" :value="true">{{ $t('open') }}</bk-radio>
                            <bk-radio :value="false">{{ $t('close') }}</bk-radio>
                        </bk-radio-group>
                    </bk-form-item>
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
            <bk-tab-panel v-if="showProxyConfigTab" name="proxyConfig" :label="$t('proxyConfig')">
                <proxy-config :base-data="repoBaseInfo" @refresh="getRepoInfoHandler"></proxy-config>
            </bk-tab-panel>
            <bk-tab-panel v-if="showCleanConfigTab" name="cleanConfig" :label="$t('cleanConfig')">
                <clean-config :base-data="repoBaseInfo" @refresh="getRepoInfoHandler"></clean-config>
            </bk-tab-panel>
            <bk-tab-panel v-if="showControlConfigTab" name="controlConfig" :label="$t('rootDirectoryPermissionTitle')">
                <control-config :base-data="repoBaseInfo" @refresh="getRepoInfoHandler" @showPermissionConfigTab="changePermissionConfigTabStatus"></control-config>
            </bk-tab-panel>
        </bk-tab>
        <div class="showPermissionConfigTab" v-if="showPermissionConfig && tabName === 'controlConfig'">
            <span style="font-weight: 500;font-size: larger;margin-left: 20px">{{ $t('permissionConfig')}}</span>
            <permission-config style="margin-top: 10px;margin-left: 20px" :base-data="repoBaseInfo" @refresh="getRepoInfoHandler"></permission-config>
        </div>
        <iam-deny-dialog :visible.sync="showIamDenyDialog" :show-data="showData"></iam-deny-dialog>
    </div>
</template>
<script>
    import proxyConfig from '@repository/views/repoConfig/proxyConfig'
    import iamDenyDialog from '@repository/components/IamDenyDialog/IamDenyDialog'
    import permissionConfig from './permissionConfig/permissionConfig'
    import cleanConfig from '@repository/views/repoConfig/cleanConfig'
    import controlConfig from '@repository/views/repoConfig/controlConfig'
    import { mapState, mapActions } from 'vuex'
    import { specialRepoEnum } from '@repository/store/publicEnum'
    export default {
        name: 'repoConfig',
        components: {
            proxyConfig,
            iamDenyDialog,
            permissionConfig,
            cleanConfig,
            controlConfig
        },
        data () {
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
                    }
                },
                showIamDenyDialog: false,
                showData: {},
                bkiamv3Check: false,
                authMode: {
                    bkiamv3Check: false
                },
                showPermissionConfig: false
            }
        },
        computed: {
            ...mapState(['domain', 'userInfo', 'permissionConfig']),
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
                return ['maven', 'pypi', 'npm', 'composer', 'nuget', 'helm'].includes(this.repoType)
            },
            showCleanConfigTab () {
                return ['docker', 'generic', 'helm'].includes(this.repoType) && (this.userInfo.admin || this.userInfo.manage)
            },
            showControlConfigTab () {
                return (this.userInfo.admin || this.userInfo.manage) && (this.authMode && !this.authMode.bkiamv3Check)
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
            this.getAuthModeFunc()
        },
        methods: {
            ...mapActions(['getRepoInfo', 'updateRepoInfo', 'getDomain', 'getPermissionUrl', 'getRootPermission', 'createOrUpdateRootPermission']),
            toRepoList () {
                this.$router.push({
                    name: 'repositories'
                })
            },
            getAuthModeFunc () {
                this.getRootPermission({
                    projectId: this.projectId,
                    repoName: this.repoName
                }).then(res => {
                    this.authMode = res
                    this.bkiamv3Check = res.bkiamv3Check
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
                                    whitelistUser: i.rules.whitelistUser.join(','),
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
                this.repoBaseInfo.loading = true
                this.updateRepoInfo({
                    projectId: this.projectId,
                    name: this.repoName,
                    body
                }).then(() => {
                    this.saveRepoMode()
                    this.getRepoInfoHandler()
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
            },
            changePermissionConfigTabStatus (val) {
                this.showPermissionConfig = val
                this.$nextTick(() => {
                    if (val) {
                        this.$refs.tab.$el.style.height = '60%'
                    } else {
                        this.$refs.tab.$el.style.height = '100%'
                    }
                })
            },
            saveRepoMode () {
                const body = {
                    projectId: this.projectId,
                    repoName: this.repoBaseInfo.name,
                    accessControlMode: this.authMode.accessControlMode,
                    officeDenyGroupSet: this.authMode.officeDenyGroupSet,
                    bkiamv3Check: this.bkiamv3Check
                }
                this.createOrUpdateRootPermission({
                    body: body
                }).then(() => {
                    this.getAuthModeFunc()
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('space') + this.$t('success')
                    })
                }).catch((err) => {
                    this.$bkMessage({
                        theme: 'error',
                        message: err.message
                    })
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
    .showPermissionConfigTab {
        background-image: none!important;
        margin-top: 10px;
        max-height: 40%;
        overflow-y: auto;
        &:before {
            content: '';
            position: absolute;
            width: 100%;
            height: 10px;
            bottom: 40%;
            background-color: var(--bgWeightColor);
        }
    }
}
</style>
