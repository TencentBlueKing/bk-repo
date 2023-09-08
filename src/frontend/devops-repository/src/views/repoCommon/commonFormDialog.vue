<template>
    <canway-dialog
        v-model="formDialog.show"
        width="400"
        height-num="193"
        :title="formDialog.title"
        @cancel="cancel">
        <bk-form :label-width="100" :model="formDialog" :rules="rules" ref="formDialog">
            <template v-if="formDialog.type === 'upgrade'">
                <bk-form-item :label="$t('upgradeTo')" :required="true" property="tag" error-display-type="normal">
                    <bk-radio-group v-model="formDialog.tag">
                        <bk-radio :disabled="Boolean(formDialog.default.length)" value="@prerelease">@prerelease</bk-radio>
                        <bk-radio class="ml20" value="@release">@release</bk-radio>
                    </bk-radio-group>
                </bk-form-item>
            </template>
            <template v-else-if="formDialog.type === 'scan'">
                <bk-form-item :label="$t('scanScheme')" :required="true" property="id" error-display-type="normal">
                    <bk-select
                        v-model="formDialog.id"
                        :placeholder="$t('scanningSchemeTip')">
                        <bk-option v-for="scan in scanList" :key="scan.id" :id="scan.id" :name="scan.name"></bk-option>
                    </bk-select>
                </bk-form-item>
            </template>
        </bk-form>
        <template #footer>
            <bk-button theme="default" @click.stop="cancel">{{$t('cancel')}}</bk-button>
            <bk-button class="ml5" :loading="formDialog.loading" theme="primary" @click="submit">{{$t('confirm')}}</bk-button>
        </template>
        <iam-deny-dialog :visible.sync="showIamDenyDialog" :show-data="showData"></iam-deny-dialog>
    </canway-dialog>
</template>
<script>
    import { mapActions, mapState } from 'vuex'
    import iamDenyDialog from '@repository/components/IamDenyDialog/IamDenyDialog'
    export default {
        name: 'commonForm',
        components: { iamDenyDialog },
        data () {
            return {
                formDialog: {
                    show: false,
                    loading: false,
                    title: '',
                    type: '',
                    version: '',
                    default: '',
                    tag: '',
                    id: '',
                    name: '',
                    path: ''
                },
                rules: {
                    tag: [
                        {
                            required: true,
                            message: this.$t('pleaseSelect') + this.$t('space') + this.$t('tag'),
                            trigger: 'blur'
                        }
                    ],
                    id: [
                        {
                            required: true,
                            message: this.$t('pleaseSelect') + this.$t('space') + this.$t('scanScheme'),
                            trigger: 'change'
                        }
                    ]
                },
                scanList: [],
                showIamDenyDialog: false,
                showData: {}
            }
        },
        computed: {
            ...mapState(['scannerSupportPackageType', 'userInfo']),
            projectId () {
                return this.$route.params.projectId
            },
            repoType () {
                return this.$route.params.repoType || ''
            },
            repoName () {
                return this.$route.query.repoName
            },
            packageKey () {
                return this.$route.query.packageKey
            }
        },
        methods: {
            ...mapActions([
                'changeStageTag',
                'startScanSingle',
                'getScanAll',
                'getPermissionUrl'
            ]),
            setData (data) {
                this.formDialog = {
                    ...this.formDialog,
                    ...data
                }
                if (data.type === 'scan') {
                    Promise.all(
                        this.scannerSupportPackageType
                            .filter(key => key.includes(this.repoType.toUpperCase()))
                            .map(type => this.getScanAll({ projectId: this.projectId, type }))
                    ).then(res => {
                        this.scanList = res.flat(1)
                    })
                }
            },
            cancel () {
                this.$refs.formDialog.clearError()
                this.formDialog.show = false
            },
            submit () {
                this.$refs.formDialog.validate().then(() => {
                    this.submitCommonForm()
                })
            },
            submitUpgrade () {
                return this.changeStageTag({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    packageKey: this.packageKey,
                    version: this.formDialog.version,
                    tag: this.formDialog.tag
                }).catch(e => {
                    if (e.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'UPDATE',
                                resourceType: 'NODE',
                                uid: this.userInfo.name,
                                repoName: this.repoName,
                                path: this.formDialog.path
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'UPDATE',
                                    url: res,
                                    path: this.formDialog.path
                                }
                            }
                        })
                    }
                })
            },
            submitScanFile () {
                const { id, version } = this.formDialog
                return this.startScanSingle({
                    id,
                    version,
                    packageKey: this.packageKey,
                    projectId: this.projectId,
                    repoType: 'MAVEN',
                    repoName: this.repoName
                })
            },
            submitCommonForm () {
                this.formDialog.loading = true
                let message = ''
                let fn = null
                switch (this.formDialog.type) {
                    case 'upgrade':
                        fn = this.submitUpgrade()
                        message = this.$t('upgrade')
                        break
                    case 'scan':
                        fn = this.submitScanFile()
                        message = this.$t('joinScanMsg')
                        break
                }
                fn.then(() => {
                    this.$emit('refresh', this.formDialog.version)
                    this.$bkMessage({
                        theme: 'success',
                        message: message + this.$t('space') + this.$t('success')
                    })
                    this.formDialog.show = false
                }).finally(() => {
                    this.formDialog.loading = false
                })
            }
        }
    }
</script>
