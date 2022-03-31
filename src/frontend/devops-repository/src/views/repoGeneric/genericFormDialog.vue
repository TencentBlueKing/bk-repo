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
                    <label class="path-tip">支持 / 分隔符级联创建文件夹</label>
                    <bk-input class="mt30"
                        v-model.trim="genericForm.path"
                        type="textarea" :rows="6"
                        :placeholder="$t('folderPathPlacehodler')">
                    </bk-input>
                </bk-form-item>
            </template>
            <template v-else-if="genericForm.type === 'rename'">
                <bk-form-item :label="$t('file') + $t('name')" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="genericForm.name" :placeholder="$t('folderNamePlacehodler')" maxlength="50" show-word-limit></bk-input>
                </bk-form-item>
            </template>
            <template v-else-if="genericForm.type === 'scan'">
                <bk-form-item label="扫描方案" :required="true" property="id" error-display-type="normal">
                    <bk-select
                        v-model="genericForm.id"
                        placeholder="请选择扫描方案">
                        <bk-option v-for="scan in scanList" :key="scan.id" :id="scan.id" :name="scan.name"></bk-option>
                    </bk-select>
                </bk-form-item>
            </template>
        </bk-form>
        <template #footer>
            <bk-button theme="default" @click="cancel">{{$t('cancel')}}</bk-button>
            <bk-button class="ml10" :loading="genericForm.loading" theme="primary" @click="submit">{{$t('confirm')}}</bk-button>
        </template>
    </canway-dialog>
</template>
<script>
    import { mapActions } from 'vuex'
    export default {
        name: 'genericForm',
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
                            message: this.$t('pleaseInput') + this.$t('folder') + this.$t('path'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^(\/[^\\:*?"<>|]{1,50})+$/,
                            message: this.$t('folderPathPlacehodler'),
                            trigger: 'blur'
                        }
                    ],
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('fileName'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^([^\\/:*?"<>|]){1,50}$/,
                            message: this.$t('folderNamePlacehodler'),
                            trigger: 'blur'
                        }
                    ],
                    id: [
                        {
                            required: true,
                            message: this.$t('pleaseSelect') + '扫描方案',
                            trigger: 'change'
                        }
                    ]
                },
                scanList: []
            }
        },
        computed: {
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
                'getScanAll'
            ]),
            setData (data) {
                this.genericForm = {
                    ...this.genericForm,
                    ...data
                }
                if (data.type === 'scan') {
                    this.getScanAll({
                        projectId: this.projectId,
                        type: 'MOBILE'
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
                return this.createFolder({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.genericForm.path.replace(/\/+/g, '/')
                })
            },
            submitRenameNode () {
                return this.renameNode({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.genericForm.path,
                    newFullPath: this.genericForm.path.replace(/[^/]*$/, this.genericForm.name)
                })
            },
            submitScanFile () {
                const { id, name, path } = this.genericForm
                return this.startScanSingle({
                    id,
                    name,
                    projectId: this.projectId,
                    repoType: 'GENERIC',
                    repoName: this.repoName,
                    fullPath: path
                })
            },
            submitGenericForm () {
                this.genericForm.loading = true
                let message = ''
                let fn = null
                switch (this.genericForm.type) {
                    case 'add':
                        fn = this.submitAddFolder()
                        message = this.$t('create') + this.$t('folder')
                        break
                    case 'rename':
                        fn = this.submitRenameNode()
                        message = this.$t('rename')
                        break
                    case 'scan':
                        fn = this.submitScanFile()
                        message = '加入扫描队列'
                        break
                }
                fn.then(() => {
                    this.$emit('refresh')
                    this.$bkMessage({
                        theme: 'success',
                        message: message + this.$t('success')
                    })
                    this.genericForm.show = false
                }).finally(() => {
                    this.genericForm.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.repo-generic-form {
    .path-tip {
        position: absolute;
        font-size: 12px;
        color: var(--fontSubsidiaryColor);
    }
}
</style>
