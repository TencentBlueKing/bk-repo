<template>
    <main class="create-node-container" v-bkloading="{ isLoading }">
        <bk-form class="mb20 plan-form" :label-width="100" :model="planForm" :rules="rules" ref="planForm">
            <bk-form-item label="计划名称" :required="true" property="name" error-display-type="normal">
                <bk-input class="w480" v-model.trim="planForm.name" maxlength="32" show-word-limit :disabled="disabled"></bk-input>
            </bk-form-item>
            <bk-form-item label="同步策略"
                :property="{ 'SPECIFIED_TIME': 'time', 'CRON_EXPRESSION': 'cron' }[planForm.executionStrategy]">
                <bk-radio-group
                    class="radio-flex"
                    v-model="planForm.executionStrategy"
                    @change="clearError">
                    <bk-radio value="IMMEDIATELY" :disabled="disabled">
                        <span>立即执行</span>
                    </bk-radio>
                    <bk-radio value="SPECIFIED_TIME" :disabled="disabled">
                        <div class="flex-align-center">
                            指定时间
                            <bk-date-picker
                                class="ml10"
                                v-if="planForm.executionStrategy === 'SPECIFIED_TIME'"
                                v-model="planForm.time"
                                type="datetime"
                                :disabled="disabled"
                                :options="{
                                    disabledDate: (date) => date < new Date()
                                }">
                            </bk-date-picker>
                        </div>
                    </bk-radio>
                    <bk-radio value="CRON_EXPRESSION" :disabled="disabled">
                        <div class="flex-align-center">
                            定时执行
                            <template v-if="planForm.executionStrategy === 'CRON_EXPRESSION'">
                                <bk-input v-if="disabled" class="ml10 w250" :value="planForm.cron" :disabled="disabled"></bk-input>
                                <Cron v-else class="ml10" v-model="planForm.cron" />
                            </template>
                        </div>
                    </bk-radio>
                    <bk-radio v-if="planForm.replicaObjectType === 'REPOSITORY'" value="REAL_TIME" :disabled="disabled">
                        <span>实时同步</span>
                    </bk-radio>
                </bk-radio-group>
            </bk-form-item>
            <bk-form-item label="冲突策略" property="conflictStrategy">
                <bk-radio-group v-model="planForm.conflictStrategy">
                    <bk-radio v-for="strategy in conflictStrategyList" :key="strategy.value" :value="strategy.value" :disabled="disabled">
                        <div class="flex-align-center">
                            {{ strategy.label }}
                            <i v-if="planForm.conflictStrategy === strategy.value" class="ml5 devops-icon icon-question-circle-shape" v-bk-tooltips="{
                                content: strategy.tip,
                                placements: ['bottom']
                            }"></i>
                        </div>
                    </bk-radio>
                </bk-radio-group>
            </bk-form-item>
            <bk-form-item label="同步类型">
                <card-radio-group
                    v-model="planForm.replicaObjectType"
                    :disabled="disabled"
                    :list="replicaObjectTypeList"
                    @change="changeReplicaObjectType">
                </card-radio-group>
            </bk-form-item>
            <bk-form-item label="同步对象" :required="true" property="config" error-display-type="normal">
                <template v-if="planForm.replicaObjectType === 'REPOSITORY'">
                    <repository-table
                        ref="planConfig"
                        :init-data="replicaTaskObjects"
                        :disabled="disabled"
                        @clearError="clearError">
                    </repository-table>
                </template>
                <template v-else-if="planForm.replicaObjectType === 'PACKAGE'">
                    <package-table
                        ref="planConfig"
                        :init-data="replicaTaskObjects"
                        :disabled="disabled"
                        @clearError="clearError">
                    </package-table>
                </template>
                <template v-else-if="planForm.replicaObjectType === 'PATH'">
                    <path-table
                        ref="planConfig"
                        :init-data="replicaTaskObjects"
                        :disabled="disabled"
                        @clearError="clearError">
                    </path-table>
                </template>
            </bk-form-item>
            <bk-form-item label="目标节点" :required="true" property="remoteClusterIds" error-display-type="normal">
                <bk-select
                    class="w480"
                    v-model="planForm.remoteClusterIds"
                    searchable
                    multiple
                    display-tag
                    :collapse-tag="false"
                    :disabled="disabled">
                    <bk-option v-for="option in clusterList.filter(v => v.type !== 'CENTER')"
                        :key="option.name"
                        :id="option.id"
                        :name="option.name">
                    </bk-option>
                </bk-select>
            </bk-form-item>
            <bk-form-item :label="$t('description')">
                <bk-input
                    class="w480"
                    v-model.trim="planForm.description"
                    type="textarea"
                    :rows="6"
                    maxlength="200"
                    :disabled="disabled">
                </bk-input>
            </bk-form-item>
            <bk-form-item>
                <bk-button @click="$router.push({ name: 'planManage' })">{{$t('cancel')}}</bk-button>
                <bk-button class="ml10" theme="primary" :loading="planForm.loading" @click="save">{{$t('confirm')}}</bk-button>
            </bk-form-item>
        </bk-form>
    </main>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import Cron from '@repository/components/Cron'
    import CardRadioGroup from '@repository/components/CardRadioGroup'
    import repositoryTable from './repositoryTable'
    import packageTable from './packageTable'
    import pathTable from './pathTable'
    export default {
        name: 'createPlan',
        components: { Cron, CardRadioGroup, repositoryTable, packageTable, pathTable },
        data () {
            return {
                isLoading: false,
                conflictStrategyList: [
                    { value: 'SKIP', label: '跳过冲突', tip: '当目标节点存在相同制品时，跳过该制品同步，同步剩余制品' },
                    { value: 'OVERWRITE', label: '替换制品', tip: '当目标节点存在相同制品时，覆盖原制品并继续执行计划' },
                    { value: 'FAST_FAIL', label: '终止同步', tip: '当目标节点存在相同制品时，终止执行计划' }
                ],
                planForm: {
                    loading: false,
                    name: '',
                    executionStrategy: 'IMMEDIATELY',
                    replicaObjectType: 'REPOSITORY',
                    time: new Date(new Date().getTime() + 30 * 60 * 1000),
                    cron: '* * * * * ? *',
                    conflictStrategy: 'SKIP',
                    remoteClusterIds: [],
                    description: ''
                },
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + '计划名称',
                            trigger: 'blur'
                        }
                    ],
                    time: [
                        {
                            required: true,
                            message: this.$t('pleaseSelect') + '时间',
                            trigger: 'blur'
                        },
                        {
                            validator: date => date > new Date(),
                            message: '当前时间已过期',
                            trigger: 'blur'
                        }
                    ],
                    cron: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + 'cron表达式',
                            trigger: 'blur'
                        }
                    ],
                    config: [
                        {
                            validator: () => {
                                return this.$refs.planConfig.getConfig()
                            },
                            message: '请完善同步对象信息',
                            trigger: 'blur'
                        }
                    ],
                    remoteClusterIds: [
                        {
                            required: true,
                            message: this.$t('pleaseSelect') + '目标节点',
                            trigger: 'blur'
                        }
                    ]
                },
                replicaTaskObjects: []
            }
        },
        computed: {
            ...mapState(['clusterList']),
            projectId () {
                return this.$route.params.projectId
            },
            routeName () {
                return this.$route.name
            },
            title () {
                return this.$route.meta.title
            },
            disabled () {
                return this.routeName === 'planDetail'
            },
            replicaObjectTypeList () {
                return [
                    { label: '仓库', value: 'REPOSITORY', tip: '同步多个仓库' },
                    { label: '制品', value: 'PACKAGE', tip: '同步同一仓库下多个制品' },
                    { label: '文件', value: 'PATH', tip: '同步同一仓库下多个文件' }
                ]
            }
        },
        created () {
            this.getRepoListAll({
                projectId: this.projectId
            })
            this.routeName !== 'createPlan' && this.handlePlanDetail()
        },
        methods: {
            ...mapActions([
                'getRepoListAll',
                'createPlan',
                'getPlanDetail',
                'updatePlan'
            ]),
            handlePlanDetail () {
                this.isLoading = true
                this.getPlanDetail({
                    key: this.$route.params.planId
                }).then(({
                    task: {
                        name,
                        replicaObjectType,
                        replicaType,
                        remoteClusters,
                        description,
                        setting: {
                            conflictStrategy,
                            executionStrategy,
                            executionPlan: { executeTime, cronExpression }
                        }
                    },
                    objects
                }) => {
                    this.planForm = {
                        ...this.planForm,
                        name,
                        executionStrategy: replicaType === 'REAL_TIME' ? 'REAL_TIME' : executionStrategy,
                        replicaObjectType,
                        ...(executeTime
                            ? {
                                time: new Date(executeTime)
                            }
                            : {}),
                        ...(cronExpression
                            ? {
                                cron: cronExpression
                            }
                            : {}),
                        conflictStrategy,
                        remoteClusterIds: remoteClusters.map(v => v.id),
                        description
                    }
                    this.replicaTaskObjects = objects
                }).finally(() => {
                    this.isLoading = false
                })
            },
            changeReplicaObjectType () {
                this.replicaTaskObjects = []
                this.planForm.executionStrategy === 'REAL_TIME' && (this.planForm.executionStrategy = 'IMMEDIATELY')
                this.clearError()
            },
            clearError () {
                this.$refs.planForm.clearError()
            },
            async save () {
                await this.$refs.planForm.validate()

                if (this.planForm.loading) return
                this.planForm.loading = true

                const replicaTaskObjects = await this.$refs.planConfig.getConfig()
                const body = {
                    name: this.planForm.name,
                    localProjectId: this.projectId,
                    replicaObjectType: this.planForm.replicaObjectType,
                    replicaTaskObjects,
                    replicaType: this.planForm.executionStrategy === 'REAL_TIME' ? 'REAL_TIME' : 'SCHEDULED',
                    setting: {
                        rateLimit: 0, // <=0不限速
                        includeMetadata: true, // 同步元数据
                        conflictStrategy: this.planForm.conflictStrategy,
                        errorStrategy: 'CONTINUE',
                        ...(this.planForm.executionStrategy !== 'REAL_TIME'
                            ? {
                                executionStrategy: this.planForm.executionStrategy,
                                executionPlan: {
                                    executeImmediately: this.planForm.executionStrategy === 'IMMEDIATELY',
                                    ...(this.planForm.executionStrategy === 'SPECIFIED_TIME'
                                        ? {
                                            // executeTime: this.planForm.time.toISOString()
                                            // 后端需要,中国时区
                                            executeTime: new Date(this.planForm.time.getTime() + 8 * 3600 * 1000).toISOString().replace(/Z$/, '')
                                        }
                                        : {}),
                                    ...(this.planForm.executionStrategy === 'CRON_EXPRESSION'
                                        ? {
                                            cronExpression: this.planForm.cron
                                        }
                                        : {})
                                }
                            }
                            : {})
                    },
                    remoteClusterIds: this.planForm.remoteClusterIds,
                    enabled: true,
                    description: this.planForm.description
                }
                const request = this.routeName === 'createPlan'
                    ? this.createPlan({ body })
                    : this.updatePlan({ body: { ...body, key: this.$route.params.planId } })
                request.then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.title + this.$t('success')
                    })
                    this.$router.back()
                }).finally(() => {
                    this.planForm.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.create-node-container {
    height: 100%;
    background-color: white;
    overflow-y: auto;
    .plan-form {
        max-width: 1200px;
        margin-top: 30px;
        margin-left: 50px;
        .arrow-right-icon {
            position: relative;
            width: 20px;
            height: 20px;
            &:before {
                position: absolute;
                content: '';
                width: 16px;
                height: 5px;
                margin: 8px 0;
                border-width: 1px 0;
                border-style: solid;
            }
            &:after {
                position: absolute;
                content: '';
                width: 10px;
                height: 10px;
                margin-left: 7px;
                margin-top: 6px;
                border-width: 1px 1px 0 0;
                border-style: solid;
                transform: rotate(45deg);
            }
        }
        .plan-object-container {
            display: grid;
            grid-template: auto / 1fr 1fr;
            margin: 5px 0 20px;
        }
        .radio-flex {
            height: 32px;
            display: flex;
            align-items: center;
            ::v-deep .bk-form-radio {
                display: flex;
                align-items: center;
                height: 32px;
                min-width: 120px;
                .bk-radio-text {
                    height: 32px;
                    display: flex;
                    align-items: center;
                }
            }
        }
        ::v-deep .bk-form-radio {
            min-width: 120px;
            margin-right: 20px;
        }
        .icon-question-circle-shape {
            color: var(--fontSubsidiaryColor);
        }
    }
}
</style>
