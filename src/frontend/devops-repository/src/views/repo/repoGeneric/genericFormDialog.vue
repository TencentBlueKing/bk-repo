<template>
    <bk-dialog
        v-model="genericForm.show"
        :title="genericForm.title"
        :close-icon="false"
        :quick-close="false"
        width="600"
        header-position="left">
        <bk-form class="repo-generic-form" :label-width="120" :model="genericForm" :rules="rules" ref="genericForm">
            <template v-if="genericForm.type === 'add'">
                <bk-form-item :label="$t('folder') + $t('path')">
                    <span class="break-all">{{ genericForm.folderPath + '/' + genericForm.path }}</span>
                </bk-form-item>
                <bk-form-item :label="$t('createFolderLabel')" :required="true" property="path">
                    <bk-input v-model.trim="genericForm.path" :placeholder="$t('folderNamePlacehodler')"></bk-input>
                </bk-form-item>
            </template>
            <template v-else-if="genericForm.type === 'rename'">
                <bk-form-item :label="$t('name')" :required="true" property="name">
                    <bk-input v-model.trim="genericForm.name" :placeholder="$t('folderNamePlacehodler')"></bk-input>
                </bk-form-item>
            </template>
            <template v-else-if="genericForm.type === 'share'">
                <bk-form-item label="授权用户" property="user">
                    <bk-tag-input
                        v-model="genericForm.user"
                        :list="Object.values(userList).filter(user => user.id !== 'anonymous')"
                        placeholder="授权访问用户，为空则任意用户可访问，按Enter键确认"
                        trigger="focus"
                        allow-create
                        has-delete-icon>
                    </bk-tag-input>
                </bk-form-item>
                <bk-form-item label="授权IP" property="ip">
                    <bk-tag-input
                        v-model="genericForm.ip"
                        placeholder="授权访问IP，为空则任意IP可访问，按Enter键确认"
                        trigger="focus"
                        allow-create
                        has-delete-icon>
                    </bk-tag-input>
                </bk-form-item>
                <bk-form-item label="访问次数" property="permits">
                    <bk-input v-model.trim="genericForm.permits" placeholder="请输入数字，小于等于0则永久有效"></bk-input>
                </bk-form-item>
                <bk-form-item :label="`${$t('validity')}(${$t('day')})`" property="time">
                    <bk-input v-model.trim="genericForm.time" placeholder="请输入数字，小于等于0则永久有效"></bk-input>
                </bk-form-item>
            </template>
        </bk-form>
        <div slot="footer">
            <bk-button :loading="genericForm.loading" theme="primary" @click="submit">{{$t('submit')}}</bk-button>
            <bk-button ext-cls="ml5" theme="default" @click="cancel">{{$t('cancel')}}</bk-button>
        </div>
    </bk-dialog>
</template>
<script>
    import { mapState } from 'vuex'
    export default {
        name: 'genericgenericForm',
        data () {
            return {
                // 新建文件夹、重命名、分享、制品晋级
                genericForm: {
                    show: false,
                    loading: false,
                    title: '',
                    type: '',
                    folderPath: '',
                    path: '',
                    name: '',
                    user: [],
                    ip: [],
                    permits: 1,
                    time: 1
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
                            regex: /^((\w|-|\.){1,50}\/)*((\w|-|\.){1,50})$/,
                            message: this.$t('folder') + this.$t('path') + this.$t('include') + this.$t('folderNamePlacehodler'),
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
                            regex: /^(\w|-|\.){1,50}$/,
                            message: this.$t('fileName') + this.$t('include') + this.$t('folderNamePlacehodler'),
                            trigger: 'blur'
                        }
                    ],
                    permits: [
                        {
                            regex: /^[0-9]*$/,
                            message: '请输入数字',
                            trigger: 'blur'
                        }
                    ],
                    time: [
                        {
                            regex: /^[0-9]*$/,
                            message: '请输入数字',
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            ...mapState(['userList'])
        },
        methods: {
            setFormData (data) {
                this.genericForm = {
                    ...this.genericForm,
                    ...data
                }
            },
            submit () {
                this.$refs.genericForm.validate().then(() => {
                    this.$emit('submit', this.genericForm)
                })
            },
            cancel () {
                this.$refs.genericForm.clearError()
                this.genericForm.show = false
            }
        }
    }
</script>
<style lang="scss" scoped>

</style>
