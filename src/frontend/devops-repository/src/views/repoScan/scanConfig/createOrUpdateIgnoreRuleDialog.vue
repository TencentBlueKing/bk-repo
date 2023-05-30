<template>
    <bk-dialog
        v-model="showDialog"
        header-position="left"
        :title="title"
        :auto-close="false"
        :mask-close="false"
        @confirm="confirm"
        width="800"
        height-num="603"
        @cancel="cancel">
        <bk-form :label-width="100" :model="ignoreRule" :rules="rules" ref="ignoreRuleForm">
            <bk-form-item :desc="$t('ruleNameTip')" desc-type="icon" :label="$t('name')" :required="true" property="name">
                <bk-input :placeholder="$t('ruleNamePlaceholder')" v-model="ignoreRule.name"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('description')" :required="false" property="description">
                <bk-input :placeholder="$t('ruleDescPlaceholder')" type="textarea" v-model="ignoreRule.description"></bk-input>
            </bk-form-item>
            <bk-form-item :desc="$t('ruleRepoNameTip')" desc-type="icon" :label="$t('repoName')" :required="false" property="repoName">
                <bk-select :placeholder="$t('ruleRepoNamePlaceholder')" v-model="ignoreRule.repoName" searchable>
                    <bk-option v-for="repo in repos" :key="repo.name" :id="repo.name" :name="repo.name"></bk-option>
                </bk-select>
            </bk-form-item>
            <bk-form-item :desc="$t('ruleFullPathTip')" desc-type="icon" :label="$t('path')" :required="false" property="fullPath">
                <bk-input :placeholder="$t('ruleFullPathPlaceholder')" v-model="ignoreRule.fullPath"></bk-input>
            </bk-form-item>
            <bk-form-item :desc="$t('rulePackageNameTip')" desc-type="icon" :label="$t('packageName')" :required="false" property="packageKey">
                <bk-input :placeholder="$t('rulePackageNamePlaceholder')" v-model="ignoreRule.packageKey"></bk-input>
            </bk-form-item>
            <bk-form-item :desc="$t('rulePackageVersionTip')" desc-type="icon" :label="$t('version')" :required="false" property="packageVersion">
                <bk-input :placeholder="$t('rulePackageVersionPlaceholder')" v-model="ignoreRule.packageVersion"></bk-input>
            </bk-form-item>
            <bk-form-item :desc="$t('ruleIgnoreRuleTypeTip')" desc-type="icon" :label="$t('ruleIgnoreRuleType')" property="type">
                <bk-radio-group v-model="ignoreRule.type">
                    <bk-radio :value="FILTER_RULE_IGNORE">{{ $t('ruleIgnoreIfMatch') }}</bk-radio>
                    <bk-radio @change="filterTypeChanged" :value="FILTER_RULE_INCLUDE">{{ $t('ruleIgnoreIfNotMatch') }}</bk-radio>
                </bk-radio-group>
            </bk-form-item>
            <bk-form-item :label="$t('ruleIgnoreMethod')">
                <bk-select v-model="selectedFilterMethod" @change="filterMethodChanged">
                    <bk-option :disabled="option.type === FILTER_METHOD_SEVERITY && ignoreRule.type === FILTER_RULE_INCLUDE" v-for="option in filterMethods" :key="option.type" :id="option.type" :name="option.name"></bk-option>
                </bk-select>
            </bk-form-item>
            <bk-form-item v-if="ignoreRule.type === FILTER_RULE_IGNORE && !ignoreAllVul && selectedFilterMethod === FILTER_METHOD_SEVERITY" :desc="$t('ruleSeverityTip')" desc-type="icon" :label="$t('ignoreRuleMinSeverity')" :required="false" property="severity">
                <bk-select :placeholder="$t('ruleSeverityPlaceholder')" v-model="ignoreRule.severity">
                    <bk-option v-for="s in severities" :key="s.level" :id="s.level" :name="s.name"></bk-option>
                </bk-select>
            </bk-form-item>
            <bk-form-item v-if="selectedFilterMethod === FILTER_METHOD_VUL_ID" :desc="$t('ruleVulTip')" desc-type="icon" :label="$t('vulnerability') + ' ID'" :required="false" property="vulIds">
                <bk-checkbox v-if="ignoreRule.type === FILTER_RULE_IGNORE" v-model="ignoreAllVul">{{ $t('ignoreAll') }}</bk-checkbox>
                <bk-input :placeholder="$t('ruleVulPlaceholder')" v-if="!ignoreAllVul" type="textarea" v-model="vulIds"></bk-input>
            </bk-form-item>
            <bk-form-item v-if="selectedFilterMethod === FILTER_METHOD_RISKY_COMPONENT" :desc="$t('ruleIgnoreRiskyComponentTip')" desc-type="icon" :label="$t('bugPackageName')" :required="false" property="riskyPackageKeys">
                <bk-input v-if="!ignoreAllVul" type="textarea" :placeholder="$t('ruleIgnoreRiskyComponentPlaceholder')" v-model="riskyPackageKeys"></bk-input>
            </bk-form-item>
            <bk-form-item v-if="selectedFilterMethod === FILTER_METHOD_RISKY_COMPONENT_VERSION" :desc="$t('ruleIgnoreRiskyComponentVersionTip')" desc-type="icon" :label="$t('riskyComponentVersionRange')" :required="false" property="riskyPackageVersions">
                <bk-input v-if="!ignoreAllVul" type="textarea" :placeholder="$t('ruleIgnoreRiskyComponentPlaceholder')" v-model="riskyPackageVersions"></bk-input>
            </bk-form-item>
        </bk-form>
    </bk-dialog>
</template>
<script>
    import { mapActions } from 'vuex'
    import {
        FILTER_METHOD_RISKY_COMPONENT,
        FILTER_METHOD_RISKY_COMPONENT_VERSION,
        FILTER_METHOD_SEVERITY,
        FILTER_METHOD_VUL_ID,
        FILTER_RULE_IGNORE,
        FILTER_RULE_INCLUDE
    } from '@/store/publicEnum'
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
                            regex: /^([\w_-]){1,255}$/,
                            message: this.$t('ruleFormatErr'),
                            trigger: 'blur'
                        }
                    ],
                    description: [
                        {
                            max: 255,
                            message: this.$t('ruleTooLong'),
                            trigger: 'blur'
                        }
                    ],
                    fullPath: [
                        {
                            max: 255,
                            message: this.$t('ruleTooLong'),
                            trigger: 'blur'
                        }
                    ],
                    packageKey: [
                        {
                            max: 255,
                            message: this.$t('ruleTooLong'),
                            trigger: 'blur'
                        }
                    ],
                    packageVersion: [
                        {
                            max: 255,
                            message: this.$t('ruleTooLong'),
                            trigger: 'blur'
                        }
                    ],
                    vulIds: [
                        {
                            validator: this.checkVulIds,
                            message: this.$t('ruleFormatErr'),
                            trigger: 'blur'
                        }
                    ]
                },
                FILTER_RULE_IGNORE: FILTER_RULE_IGNORE,
                FILTER_RULE_INCLUDE: FILTER_RULE_INCLUDE,
                FILTER_METHOD_VUL_ID: FILTER_METHOD_VUL_ID,
                FILTER_METHOD_SEVERITY: FILTER_METHOD_SEVERITY,
                FILTER_METHOD_RISKY_COMPONENT: FILTER_METHOD_RISKY_COMPONENT,
                FILTER_METHOD_RISKY_COMPONENT_VERSION: FILTER_METHOD_RISKY_COMPONENT_VERSION,
                showDialog: false,
                ignoreRule: {},
                vulIds: '',
                riskyPackageKeys: '',
                riskyPackageVersions: '',
                ignoreAllVul: false,
                title: '',
                repos: [],
                filterMethods: [
                    {
                        type: FILTER_METHOD_VUL_ID,
                        name: this.$t('ruleIgnoreByVulId')
                    },
                    {
                        type: FILTER_METHOD_SEVERITY,
                        name: this.$t('ruleIgnoreByVulSeverity')
                    },
                    {
                        type: FILTER_METHOD_RISKY_COMPONENT,
                        name: this.$t('ruleIgnoreByVulComponent')
                    },
                    {
                        type: FILTER_METHOD_RISKY_COMPONENT_VERSION,
                        name: this.$t('ruleIgnoreByVulComponentVersion')
                    }
                ],
                selectedFilterMethod: FILTER_METHOD_VUL_ID,
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
                            type: FILTER_RULE_IGNORE,
                            name: ''
                        }
                        this.ignoreRule.projectId = this.projectId
                        this.ignoreRule.planId = this.planId
                    }

                    if (this.ignoreRule.severity) {
                        this.selectedFilterMethod = FILTER_METHOD_SEVERITY
                    } else if (this.ignoreRule.riskyPackageKeys) {
                        this.selectedFilterMethod = FILTER_METHOD_RISKY_COMPONENT
                    } else if (this.ignoreRule.riskyPackageVersions) {
                        this.selectedFilterMethod = FILTER_METHOD_RISKY_COMPONENT_VERSION
                    } else {
                        this.selectedFilterMethod = FILTER_METHOD_VUL_ID
                    }

                    if (this.ignoreRule.riskyPackageVersions) {
                        const pkgVersionRange = []
                        for (const pkg in this.ignoreRule.riskyPackageVersions) {
                            pkgVersionRange.push(`${pkg} ${this.ignoreRule.riskyPackageVersions[pkg]}`)
                        }
                        this.riskyPackageVersions = pkgVersionRange.join('\n')
                    } else {
                        this.riskyPackageVersions = ''
                    }

                    this.ignoreAllVul = this.ignoreRule.vulIds !== undefined && this.ignoreRule.vulIds !== null && this.ignoreRule.vulIds.length === 0
                    this.vulIds = this.ignoreRule.vulIds ? this.ignoreRule.vulIds.join('\n') : ''
                    this.riskyPackageKeys = this.ignoreRule.riskyPackageKeys ? this.ignoreRule.riskyPackageKeys.join('\n') : ''
                    this.title = (this.ignoreRule.id ? this.$t('update') : this.$t('create')) + this.$t('rule')
                }
            }
        },
        methods: {
            ...mapActions(['updateIgnoreRule', 'createIgnoreRule', 'getRepoListWithoutPage']),
            filterMethodChanged (newVal, oldVal) {
                if (newVal !== FILTER_METHOD_VUL_ID) {
                    this.vulIds = ''
                    this.ignoreAllVul = false
                }
                if (newVal !== FILTER_METHOD_SEVERITY) {
                    this.ignoreRule.severity = undefined
                }
                if (newVal !== FILTER_METHOD_RISKY_COMPONENT) {
                    this.riskyPackageKeys = ''
                }
                if (newVal !== FILTER_METHOD_RISKY_COMPONENT_VERSION) {
                    this.riskyPackageVersions = ''
                }
            },
            filterTypeChanged (val) {
                this.ignoreRule.severity = null
                this.ignoreAllVul = false
            },
            checkVulIds () {
                return this.ignoreAllVul || /^[\w\n_-]+$/.test(this.vulIds)
            },
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
                if (this.riskyPackageKeys) {
                    this.ignoreRule.riskyPackageKeys = this.riskyPackageKeys.trim().split('\n')
                }

                if (this.riskyPackageVersions) {
                    this.ignoreRule.riskyPackageVersions = {}
                    const versions = this.riskyPackageVersions.trim().split(/\n+/)
                    versions.forEach(v => {
                        const trimVer = v.trim()
                        const indexOfSpace = trimVer.indexOf(' ')
                        this.ignoreRule.riskyPackageVersions[trimVer.substring(0, indexOfSpace)] = trimVer.substring(indexOfSpace).trim()
                    })
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
    .bk-form-radio {
        margin-right: 10px;
    }
</style>
