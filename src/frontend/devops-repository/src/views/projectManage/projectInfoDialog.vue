<template>
    <canway-dialog
        v-model="editProjectDialog.show"
        :title="editProjectDialog.add ? '新建项目' : '编辑项目'"
        width="500"
        height-num="354"
        @cancel="editProjectDialog.show = false">
        <bk-form class="ml10 mr10" :label-width="75" :model="editProjectDialog" :rules="rules" ref="projectInfoForm">
            <bk-form-item label="项目标识" :required="true" property="id" error-display-type="normal">
                <bk-input v-model.trim="editProjectDialog.id"
                    :disabled="!editProjectDialog.add" maxlength="32"
                    show-word-limit
                    placeholder="请输入2-32字符的小写字母+数字组合，以字母开头">
                </bk-input>
            </bk-form-item>
            <bk-form-item label="项目名称" :required="true" property="name" error-display-type="normal">
                <bk-input v-model.trim="editProjectDialog.name" maxlength="32" show-word-limit></bk-input>
            </bk-form-item>
            <bk-form-item label="项目描述" property="description">
                <bk-input type="textarea" v-model.trim="editProjectDialog.description" maxlength="200" show-word-limit></bk-input>
            </bk-form-item>
        </bk-form>
        <template #footer>
            <bk-button theme="default" @click.stop="editProjectDialog.show = false">{{$t('cancel')}}</bk-button>
            <bk-button class="ml10" :loading="editProjectDialog.loading" theme="primary" @click.stop.prevent="submitProject()">{{$t('confirm')}}</bk-button>
        </template>
    </canway-dialog>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'projectInfoDialog',
        data () {
            return {
                editProjectDialog: {
                    show: false,
                    loading: false,
                    add: true,
                    id: '',
                    name: '',
                    description: ''
                },
                rules: {
                    id: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + '项目标识',
                            trigger: 'blur'
                        },
                        {
                            regex: /^[a-z][a-z0-9]{1,31}$/,
                            message: '请输入2-32字符的小写字母+数字组合，以字母开头',
                            trigger: 'blur'
                        },
                        {
                            validator: id => this.asynCheck({ id }),
                            message: '项目标识已存在',
                            trigger: 'blur'
                        }
                    ],
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + '项目名称',
                            trigger: 'blur'
                        },
                        {
                            validator: name => this.asynCheck({ name }),
                            message: '项目名称已存在',
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            ...mapState(['projectList'])
        },
        methods: {
            ...mapActions([
                'getProjectList',
                'createProject',
                'editProject',
                'checkProject'
            ]),
            setData (data) {
                this.$refs.projectInfoForm.clearError()
                this.editProjectDialog = {
                    ...this.editProjectDialog,
                    ...data
                }
            },
            asynCheck ({ id, name }) {
                if (!this.editProjectDialog.add) {
                    const project = this.projectList.find(v => v.id === this.editProjectDialog.id)
                    if (!name || project.name === name) return true
                }
                return this.checkProject({ id, name }).then(res => !res)
            },
            async submitProject () {
                await this.$refs.projectInfoForm.validate()
                this.editProjectDialog.loading = true
                const { id, name, description } = this.editProjectDialog
                const fn = this.editProjectDialog.add ? this.createProject : this.editProject
                fn({
                    id,
                    name,
                    description
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (this.editProjectDialog.add ? '新建项目' : '编辑项目') + this.$t('success')
                    })
                    this.editProjectDialog.show = false
                    this.getProjectList()
                }).finally(() => {
                    this.editProjectDialog.loading = false
                })
            }
        }
    }
</script>
