<template>
    <div>
        <bk-form class="control-config-container" :label-width="120" :model="controlConfigs" ref="controlForm" :rules="rules">
            <bk-form-item :label="$t('accessPermission')">
                <card-radio-group
                    class="permission-card"
                    v-model="available"
                    :list="availableList">
                </card-radio-group>
            </bk-form-item>
            <template v-if="(repoType === 'generic' || repoType === 'ddc') && repoName !== 'pipeline'">
                <bk-form-item v-for="type in genericInterceptorsList" :key="type"
                    :label="$t(`${type}Download`)" :property="`${type}.enable`">
                    <bk-radio-group v-model="controlConfigs[type].enable">
                        <bk-radio class="mr20" :value="true">{{ $t('enable') }}</bk-radio>
                        <bk-radio :value="false">{{ $t('disable') }}</bk-radio>
                    </bk-radio-group>
                    <template v-if="controlConfigs[type].enable && ['mobile', 'web'].includes(type)">
                        <bk-form-item :label="$t('fileName')" :label-width="80" class="mt10"
                            :property="`${type}.filename`" required error-display-type="normal">
                            <bk-input class="w250" v-model.trim="controlConfigs[type].filename"></bk-input>
                            <i class="bk-icon icon-info f14 ml5" v-bk-tooltips="$t('fileNameRule')"></i>
                        </bk-form-item>
                        <bk-form-item :label="$t('metadata')" :label-width="80"
                            :property="`${type}.metadata`" required error-display-type="normal">
                            <bk-input class="w250" v-model.trim="controlConfigs[type].metadata" :placeholder="$t('metadataRule')"></bk-input>
                            <a class="f12 ml5" href="https://bk.tencent.com/docs/markdown/ZH/Devops/2.0/UserGuide/Services/Artifactory/meta.md" target="__blank">{{ $t('viewMetadataDocument') }}</a>
                        </bk-form-item>
                    </template>
                    <template v-if="controlConfigs[type].enable && type === 'ip_segment'">
                        <bk-form-item :label="$t('IP')" :label-width="150" class="mt10"
                            :property="`${type}.ipSegment`" :required="!controlConfigs[type].officeNetwork" error-display-type="normal">
                            <bk-input class="w250 mr10" v-model.trim="controlConfigs[type].ipSegment" :placeholder="$t('ipPlaceholder')" :maxlength="4096"></bk-input>
                            <bk-checkbox v-model="controlConfigs[type].officeNetwork">{{ $t('office_networkDownload') }}</bk-checkbox>
                            <i class="bk-icon icon-info f14 ml5" v-bk-tooltips="$t('office_networkDownloadTips')"></i>
                        </bk-form-item>
                        <bk-form-item :label="$t('whiteUser')" :label-width="150"
                            :property="`${type}.whitelistUser`" error-display-type="normal">
                            <bk-input class="w250" v-model.trim="controlConfigs[type].whitelistUser" :placeholder="$t('whiteUserPlaceholder')"></bk-input>
                        </bk-form-item>
                    </template>
                </bk-form-item>
            </template>
            <bk-form-item :label="$t('blackUserList')" property="blackList" error-display-type="normal" v-if="isDevx">
                <div class="mb10 flex-between-center">
                    <bk-select
                        v-model="blackList"
                        class="bkre-user-select"
                        :multiple="true"
                        searchable
                        :placeholder="$t('controlConfigPlaceholder')">
                        <bk-option v-for="option in roleList"
                            :key="option.id"
                            :id="option.id"
                            :name="option.name">
                        </bk-option>
                    </bk-select>
                    <bk-link theme="primary" @click="manageUserGroup" style="margin-right: auto;margin-left: 10px">{{ $t('manage') + $t('space') + $t('userGroup') }}</bk-link>
                </div>
            </bk-form-item>
            <bk-form-item v-if="repoName !== 'pipeline'">
                <bk-button theme="primary" @click="save()">{{$t('save')}}</bk-button>
            </bk-form-item>
        </bk-form>
        <add-user-dialog ref="addUserDialog" :visible.sync="showAddUserDialog" @complete="handleAddUsers"></add-user-dialog>
    </div>
</template>
<script>
    import { mapActions } from 'vuex'
    import AddUserDialog from '@repository/components/AddUserDialog/addUserDialog'
    import { specialRepoEnum } from '@repository/store/publicEnum'
    import CardRadioGroup from '@repository/components/CardRadioGroup'

    export default {
        name: 'controlConfig',
        components: { CardRadioGroup, AddUserDialog },
        props: {
            baseData: Object
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
                rootDirectoryPermission: '',
                controlConfigs: {
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
                blackList: [],
                showAddUserDialog: false,
                filenameRule,
                metadataRule,
                ipSegmentRule,
                roleList: []
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
            isDevx () {
                return RELEASE_MODE === 'devx'
            },
            isTencent () {
                return RELEASE_MODE === 'tencent'
            },
            genericInterceptorsList () {
                return this.isTencent ? ['mobile', 'web', 'ip_segment'] : ['mobile', 'web']
            },
            available: {
                get () {
                    if (this.baseData.name === 'pipeline') return 'pipeline'
                    if (this.baseData.public) {
                        this.$emit('showPermissionConfigTab', false)
                        return 'public'
                    }
                    if (this.rootDirectoryPermission === 'DIR_CTRL') {
                        this.$emit('showPermissionConfigTab', true)
                        return 'folder'
                    }
                    if (this.rootDirectoryPermission === 'STRICT') {
                        this.$emit('showPermissionConfigTab', true)
                        return 'strict'
                    }
                    this.$emit('showPermissionConfigTab', false)
                    return 'default'
                },
                set (val) {
                    if (val === 'public') {
                        this.$emit('showPermissionConfigTab', false)
                        this.baseData.public = true
                        this.rootDirectoryPermission = null
                    } else if (val === 'folder') {
                        this.$emit('showPermissionConfigTab', true)
                        this.baseData.public = false
                        this.rootDirectoryPermission = 'DIR_CTRL'
                    } else if (val === 'strict') {
                        this.$emit('showPermissionConfigTab', true)
                        this.baseData.public = false
                        this.rootDirectoryPermission = 'STRICT'
                    } else if (val === 'default') {
                        this.$emit('showPermissionConfigTab', false)
                        this.baseData.public = false
                        this.rootDirectoryPermission = 'DEFAULT'
                    } else {
                        this.$emit('showPermissionConfigTab', false)
                    }
                }
            },
            availableList () {
                if (this.baseData.repoType === 'generic' || this.baseData.repoType === 'ddc') {
                    if (this.baseData.name === 'pipeline') {
                        return [
                            { label: this.$t('permissionTitle.pipeline'), value: 'pipeline', tip: this.$t('permissionTip.pipeline') }
                        ]
                    } else {
                        return [
                            { label: this.$t('permissionTitle.default'), value: 'default', tip: this.$t('permissionTip.default') },
                            { label: this.$t('permissionTitle.strict'), value: 'strict', tip: this.$t('permissionTip.strict') },
                            { label: this.$t('permissionTitle.folder'), value: 'folder', tip: this.$t('permissionTip.folder') },
                            { label: this.$t('permissionTitle.public'), value: 'public', tip: this.$t('permissionTip.public') }
                        ]
                    }
                } else {
                    return [
                        { label: this.$t('permissionTitle.default'), value: 'default', tip: this.$t('permissionTip.default') },
                        { label: this.$t('permissionTitle.public'), value: 'public', tip: this.$t('permissionTip.public') }
                    ]
                }
            },
            rules () {
                return {
                    'mobile.filename': this.filenameRule,
                    'mobile.metadata': this.metadataRule,
                    'web.filename': this.filenameRule,
                    'web.metadata': this.metadataRule,
                    'ip_segment.ipSegment': this.controlConfigs.ip_segment.officeNetwork ? {} : this.ipSegmentRule
                }
            }
        },
        watch: {
            baseData: {
                handler (val) {
                    this.controlConfigs = val
                },
                deep: true,
                immediate: true
            }
        },
        created () {
            this.getRootPermission({
                projectId: this.projectId,
                repoName: this.repoName
            }).then((res) => {
                this.rootDirectoryPermission = res.accessControlMode
                this.blackList = res.officeDenyGroupSet
            })
            this.getRoleListHandler()
        },
        methods: {
            ...mapActions(['updateRepoInfo', 'getRootPermission', 'getProjectRoleList', 'createOrUpdateRootPermission']),
            getRoleListHandler () {
                this.getProjectRoleList({ projectId: this.projectId }).then(res => {
                    res.forEach(role => {
                        this.roleList.push({
                            id: role.id,
                            name: role.name
                        })
                    })
                })
            },
            manageUserGroup () {
                this.$router.replace({
                    name: 'userGroup'
                })
            },
            addUserGroup () {
                this.$refs.roleForm.clearError()
                this.editRoleConfig = {
                    show: true,
                    loading: false,
                    id: '',
                    name: '',
                    description: '',
                    users: [],
                    originUsers: []
                }
            },
            showAddDialog () {
                this.showAddUserDialog = true
                this.$refs.addUserDialog.editUserConfig = {
                    users: this.editRoleConfig.users,
                    originUsers: this.editRoleConfig.originUsers,
                    search: '',
                    newUser: ''
                }
            },
            handleAddUsers (users) {
                this.editRoleConfig.originUsers = users
                this.editRoleConfig.users = users
            },
            deleteUser (index) {
                const temp = []
                for (let i = 0; i < this.editRoleConfig.users.length; i++) {
                    if (i !== index) {
                        temp.push(this.editRoleConfig.users[i])
                    }
                }
                this.editRoleConfig.users = temp
                this.editRoleConfig.originUsers = temp
            },
            async save () {
                await this.$refs.controlForm.validate()
                try {
                    let count = 0
                    const modeBody = {
                        projectId: this.projectId,
                        repoName: this.repoName,
                        accessControlMode: this.rootDirectoryPermission,
                        officeDenyGroupSet: this.blackList
                    }
                    const configBody = this.getRepoConfigBody()
                    await this.updateRepoInfo({
                        projectId: this.projectId,
                        name: this.repoName,
                        body: configBody
                    }).then(
                        count = count + 1
                    )
                    await this.createOrUpdateRootPermission({
                        body: modeBody
                    }).then(() => {
                        count = count + 1
                    })
                    if (count === 2) {
                        this.$emit('refresh')
                        this.$bkMessage({
                            theme: 'success',
                            message: this.$t('save') + this.$t('space') + this.$t('success')
                        })
                    }
                } catch (e) {
                    this.$emit('refresh')
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('save') + this.$t('space') + this.$t('fail')
                    })
                }
            },
            getRepoConfigBody () {
                const interceptors = []
                if (this.repoType === 'generic') {
                    ['mobile', 'web', 'ip_segment'].forEach(type => {
                        const { enable, filename, metadata, ipSegment, whitelistUser, officeNetwork } = this.baseData[type]
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
                    public: this.baseData.public,
                    description: this.baseData.description,
                    display: this.baseData.display,
                    configuration: {
                        ...this.baseData.configuration,
                        settings: {
                            system: this.baseData.system,
                            interceptors: interceptors.length ? interceptors : undefined,
                            ...(
                                this.repoType === 'rpm'
                                    ? {
                                        enabledFileLists: this.baseData.enabledFileLists,
                                        repodataDepth: this.baseData.repodataDepth,
                                        groupXmlSet: this.baseData.groupXmlSet
                                    }
                                    : {}
                            )
                        }
                    }
                }
                if (!specialRepoEnum.includes(this.baseData.name)) {
                    body.configuration.settings.bkiamv3Check = this.baseData.configuration.settings.bkiamv3Check
                }
                return body
            }
        }
    }
</script>
<style lang="scss" scoped>
.control-config-container {
    .permission-card {
        display: grid;
        grid-template: auto / repeat(2, 1fr);
        max-width: 500px;
        ::v-deep .bk-form-radio-button {
            margin-top: 10px;
            .card-radio {
                min-width: 180px;
                max-width: 315px;
                height: 90px;
                .card-tip {
                    word-break: break-all;
                    white-space: normal;
                    width: 300px;
                }
            }
            .bk-radio-button-text {
                height: auto;
                line-height: initial;
                padding: 0;
                border: 0 none;
            }
        }
    }
    .bkre-user-select {
        width: 300px;
        background-color: #FFFFFF1A;
        &:hover {
            background-color: rgba(255, 255, 255, 0.4);
        }
    }
}
</style>
