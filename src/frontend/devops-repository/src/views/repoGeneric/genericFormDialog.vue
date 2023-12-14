<template>
    <canway-dialog
        v-model="genericForm.show"
        :title="genericForm.title"
        width="450"
        :height-num="311"
        @cancel="cancel">
        <bk-form class="mr10 repo-generic-form" :label-width="90" :model="genericForm" :rules="rules" ref="genericForm">
            <template v-if="genericForm.type === 'add'">
                <bk-form-item :label="$t('createFolderLabel')" :required="true" property="path" error-display-type="normal">
                    <bk-input v-model.trim="genericForm.path"
                        type="textarea" :rows="6"
                        :placeholder="$t('folderPathPlaceholder')">
                    </bk-input>
                    <div class="form-tip">{{$t('genericFormTip')}}</div>
                </bk-form-item>
            </template>
            <template v-else-if="genericForm.type === 'rename'">
                <bk-form-item :label="$t('file') + $t('name')" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="genericForm.name" :placeholder="$t('folderNamePlaceholder')" maxlength="255" show-word-limit></bk-input>
                </bk-form-item>
            </template>
            <template v-else-if="genericForm.type === 'scan'">
                <bk-form-item :label="$t('scanScheme')" :required="true" property="id" error-display-type="normal">
                    <bk-select
                        v-model="genericForm.id"
                        :placeholder="$t('scanningSchemeTip')">
                        <bk-option v-for="scan in scanList" :key="scan.id" :id="scan.id" :name="scan.name"></bk-option>
                    </bk-select>
                </bk-form-item>
            </template>
        </bk-form>
        <template #footer>
            <bk-button theme="default" @click="cancel">{{$t('cancel')}}</bk-button>
            <bk-button class="ml10" :loading="genericForm.loading" theme="primary" @click="submit">{{$t('confirm')}}</bk-button>
        </template>
        <iam-deny-dialog :visible.sync="showIamDenyDialog" :show-data="showData"></iam-deny-dialog>
    </canway-dialog>
</template>
<script>
    import { mapActions, mapState } from 'vuex'
    import iamDenyDialog from '@repository/components/IamDenyDialog/IamDenyDialog'
    export default {
        name: 'genericForm',
        components: {
            iamDenyDialog
        },
        data () {
            return {
                genericForm: {
                    show: false,
                    loading: false,
                    title: '',
                    type: '',
                    path: '',
                    name: '',
                    id: ''
                },
                // genericForm Rules
                rules: {
                    path: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('folder') + this.$t('space') + this.$t('path'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^(\/[^\\:*?"<>|]{1,255})+$/,
                            message: this.$t('folderPathPlaceholder'),
                            trigger: 'blur'
                        }
                    ],
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('fileName'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^([^\\/:*?"<>|]){1,255}$/,
                            message: this.$t('folderNamePlaceholder'),
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
                showData: {},
                webError: false
            }
        },
        computed: {
            ...mapState(['userInfo']),
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            }
        },
        methods: {
            ...mapActions([
                'createFolder',
                'renameNode',
                'startScanSingle',
                'getScanAll',
                'getPermissionUrl'
            ]),
            setData (data) {
                this.genericForm = {
                    ...this.genericForm,
                    ...data
                }
                if (data.type === 'scan') {
                    let fileNameExt = ''
                    const lastIndexOfDot = this.genericForm.path.lastIndexOf('.')
                    if (lastIndexOfDot !== -1) {
                        fileNameExt = this.genericForm.path.substring(lastIndexOfDot + 1)
                    }
                    this.getScanAll({
                        projectId: this.projectId,
                        type: 'GENERIC',
                        fileNameExt: fileNameExt
                    }).then(res => {
                        this.scanList = res
                    })
                }
            },
            cancel () {
                this.$refs.genericForm.clearError()
                this.genericForm.show = false
            },
            submit () {
                this.$refs.genericForm.validate().then(() => {
                    this.submitGenericForm()
                })
            },
            submitAddFolder () {
                this.webError = false
                return this.createFolder({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.genericForm.path.replace(/\/+/g, '/')
                }).catch(err => {
                    if (err.status === 403) {
                        this.webError = true
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'WRITE',
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
                                    action: 'WRITE',
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
                })
            },
            submitRenameNode () {
                this.webError = false
                return this.renameNode({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.genericForm.path,
                    newFullPath: this.genericForm.path.replace(/[^/]*$/, this.genericForm.name)
                }).catch(err => {
                    if (err.status === 403) {
                        this.webError = true
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'UPDATE',
                                resourceType: 'NODE',
                                uid: this.userInfo.name,
                                repoName: this.repoName,
                                path: this.genericForm.path
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'UPDATE',
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
                })
            },
            submitScanFile () {
                this.webError = false
                const { id, path } = this.genericForm
                return this.startScanSingle({
                    id,
                    projectId: this.projectId,
                    repoType: 'GENERIC',
                    repoName: this.repoName,
                    fullPath: path
                }).catch(err => {
                    if (err.status === 403) {
                        this.webError = true
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'UPDATE',
                                resourceType: 'NODE',
                                uid: this.userInfo.name,
                                repoName: this.repoName,
                                path: this.genericForm.path
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'UPDATE',
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
                })
            },
            submitGenericForm () {
                this.genericForm.loading = true
                let message = ''
                let fn = null
                switch (this.genericForm.type) {
                    case 'add':
                        fn = this.submitAddFolder()
                        message = this.$t('create') + this.$t('space') + this.$t('folder')
                        break
                    case 'rename':
                        fn = this.submitRenameNode()
                        message = this.$t('rename')
                        break
                    case 'scan':
                        fn = this.submitScanFile()
                        message = this.$t('joinScanMsg')
                        break
                }
                fn.then(() => {
                    this.$emit('refresh')
                    if (!this.webError) {
                        this.$bkMessage({
                            theme: 'success',
                            message: message + this.$t('space') + this.$t('success')
                        })
                    }
                    this.genericForm.show = false
                }).finally(() => {
                    this.genericForm.loading = false
                })
            }
        }
    }
</script>
