<template>
    <bk-dialog
        v-model="showDialog"
        header-position="left"
        :title="title"
        :mask-close="false"
        @confirm="confirm"
        width="800"
        height-num="603"
        @cancel="cancel">
        <bk-form :label-width="80" :model="ignoreRule" :rules="rules" ref="ignoreRuleForm">
            <bk-form-item :label="$t('name')" :required="true" property="name">
                <bk-input v-model="ignoreRule.name"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('description')" :required="false" property="description">
                <bk-input type="textarea" v-model="ignoreRule.description"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('repoName')" :required="false" property="repoName">
                <bk-select v-model="ignoreRule.repoName" searchable>
                    <bk-option v-for="repo in repos" :key="repo.name" :id="repo.name" :name="repo.name"></bk-option>
                </bk-select>
            </bk-form-item>
            <bk-form-item :label="$t('path')" :required="false" property="fullPath">
                <bk-input v-model="ignoreRule.fullPath"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('packageName')" :required="false" property="packageKey">
                <bk-input v-model="ignoreRule.packageKey"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('version')" :required="false" property="packageVersion">
                <bk-input v-model="ignoreRule.packageVersion"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('vulnerability') + ' ID'" :required="false">
                <bk-checkbox v-model="ignoreAllVul">{{ $t('ignoreAll') }}</bk-checkbox>
                <bk-input v-if="!ignoreAllVul" type="textarea" :planceholder="$t('ignoreRuleVulIdsPlaceholder')" v-model="vulIds"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('ignoreRuleMinSeverity')" :required="false" property="severity">
                <bk-select v-model="ignoreRule.severity">
                    <bk-option v-for="s in severities" :key="s.level" :id="s.level" :name="s.name"></bk-option>
                </bk-select>
            </bk-form-item>
        </bk-form>
    </bk-dialog>
</template>
<script>
    import { mapActions } from 'vuex'
    export default {
        name: 'createOrUpdateIgnoreRuleDialog',
        props: {
            visible: Boolean,
            projectId: String,
            planId: String,
            updatingRule: {
                type: Object,
                default: null
            }
        },
        data () {
            return {
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('name'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^([\w\d_-]){1,255}$/,
                            message: this.$t('ruleNameError'),
                            trigger: 'blur'
                        }
                    ]
                },
                showDialog: false,
                ignoreRule: {},
                vulIds: '',
                ignoreAllVul: false,
                title: '',
                repos: [],
                severities: [
                    {
                        name: 'Critical',
                        level: 3
                    },
                    {
                        name: 'High',
                        level: 2
                    },
                    {
                        name: 'Medium',
                        level: 1
                    },
                    {
                        name: 'Low',
                        level: 0
                    }
                ]
            }
        },
        watch: {
            visible: function (newVal) {
                this.showDialog = newVal
                if (newVal) {
                    this.refreshRepos()
                    if (this.updatingRule) {
                        this.ignoreRule = this.updatingRule
                    } else {
                        this.ignoreRule = {
                            vulIds: []
                        }
                        this.ignoreRule.projectId = this.projectId
                        this.ignoreRule.planId = this.planId
                    }
                    this.vulIds = this.ignoreRule.vulIds ? this.ignoreRule.vulIds.join('\n') : ''
                    this.title = this.ignoreRule.id ? this.$t('update') : this.$t('create')
                }
            }
        },
        methods: {
            ...mapActions(['updateIgnoreRule', 'createIgnoreRule', 'getRepoListWithoutPage']),
            cancel () {
                this.$emit('update:visible', false)
            },
            confirm () {
                this.$refs.ignoreRuleForm.validate().then(() => {
                    this.updateOrCreate()
                })
            },
            updateOrCreate () {
                this.ignoreRule.description = this.ignoreRule.description ? this.ignoreRule.description : ''
                if (this.ignoreAllVul) {
                    this.ignoreRule.vulIds = []
                } else {
                    this.ignoreRule.vulIds = this.vulIds ? this.vulIds.trim().split('\n') : null
                }
                const promise = this.ignoreRule.id
                    ? this.updateIgnoreRule(this.ignoreRule)
                    : this.createIgnoreRule(this.ignoreRule)
                promise.then(_ => {
                    this.$emit('success')
                    this.$emit('update:visible', false)
                    this.$bkMessage({
                        theme: 'success',
                        message: this.title + this.$t('space') + this.$t('success')
                    })
                })
            },
            refreshRepos () {
                this.getRepoListWithoutPage({
                    projectId: this.projectId
                }).then(({ records }) => {
                    this.repos = records.filter(v => {
                        return v.name !== 'report' && v.name !== 'log'
                    })
                }).finally(() => {
                    this.isLoading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>

</style>
