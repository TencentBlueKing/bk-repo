<template>
    <canway-dialog
        v-model="genericForm.show"
        :title="genericForm.title"
        width="450"
        :height-num="193"
        @cancel="cancel">
        <bk-form class="repo-generic-form" :label-width="100" :model="genericForm" :rules="rules" ref="genericForm">
            <template v-if="genericForm.type === 'add'">
                <bk-form-item :label="$t('createFolderLabel')" :required="true" property="path" error-display-type="normal">
                    <label class="path-tip">支持 / 分隔符级联创建文件夹</label>
                    <bk-input v-model.trim="genericForm.path" :placeholder="$t('folderNamePlacehodler')"></bk-input>
                </bk-form-item>
            </template>
            <template v-else-if="genericForm.type === 'rename'">
                <bk-form-item :label="$t('file') + $t('name')" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="genericForm.name" :placeholder="$t('folderNamePlacehodler')" maxlength="50" show-word-limit></bk-input>
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
                    name: ''
                },
                // genericForm Rules
                rules: {
                    path: [
                        {
                            regex: /[^\\/:*?"<>|]$/,
                            message: this.$t('pleaseInput') + this.$t('folder') + this.$t('path'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^([^\\:*?"<>|]){1,50}$/,
                            message: this.$t('folderNamePlacehodler'),
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
            }
        },
        methods: {
            ...mapActions(['createFolder', 'renameNode']),
            setData (data) {
                this.genericForm = {
                    ...this.genericForm,
                    ...data
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
        margin-top: -26px;
        font-size: 12px;
        color: var(--fontSubsidiaryColor);
    }
}
</style>
