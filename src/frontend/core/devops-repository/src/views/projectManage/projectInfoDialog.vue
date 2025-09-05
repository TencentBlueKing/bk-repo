<template>
    <canway-dialog
        v-model="editProjectDialog.show"
        :title="editProjectDialog.add ? $t('createProject') : $t('editProject')"
        width="500"
        height-num="354"
        @cancel="editProjectDialog.show = false">
        <bk-form class="ml10 mr10" :label-width="75" :model="editProjectDialog" :rules="rules" ref="projectInfoForm">
            <bk-form-item :label="$t('projectId')" :required="true" property="id" error-display-type="normal">
                <bk-input v-model.trim="editProjectDialog.id"
                    :disabled="!editProjectDialog.add" maxlength="100"
                    show-word-limit
                    :placeholder="$t('numCharacterTip')">
                </bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('projectName')" :required="true" property="name" error-display-type="normal">
                <bk-input v-model.trim="editProjectDialog.name" maxlength="100" show-word-limit></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('projectDescription')" property="description">
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
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('projectId'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^[a-z][a-z0-9_.-]{1,99}$/,
                            message: this.$t('numCharacterTip'),
                            trigger: 'blur'
                        },
                        {
                            validator: id => this.asynCheck({ id }),
                            message: this.$t('projectId') + this.$t('space') + this.$t('exist'),
                            trigger: 'blur'
                        }
                    ],
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('projectName'),
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
                'checkProject',
                'checkProjectId'
            ]),
            setData (data) {
                this.$refs.projectInfoForm.clearError()
                this.editProjectDialog = {
                    ...this.editProjectDialog,
                    ...data
                }
            },
            asynCheck ({ id }) {
                return this.checkProjectId({ id }).then(res => !res)
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
                        message: (this.editProjectDialog.add ? this.$t('createProject') : this.$t('editProject')) + this.$t('space') + this.$t('success')
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
