<template>
    <div class="arti-table-container">
        <bk-radio-group v-model="showAddBtn">
            <bk-radio :disabled="disabled" :value="false">所有制品的最新版本</bk-radio>
            <bk-radio :disabled="disabled" class="mt10" :value="true">
                <span>满足规则的制品</span>
                <div v-show="showAddBtn && !disabled" class="ml10 rule-add flex-center" @click="addRule()">
                    <i class="mr5 devops-icon icon-plus-circle"></i>
                    添加规则
                </div>
            </bk-radio>
        </bk-radio-group>
        <div v-show="showAddBtn" class="rule-list">
            <component :is="ruleType"
                class="mt10"
                v-for="(rule, ind) in defaultRules"
                :key="ind"
                :disabled="disabled"
                v-bind="rule"
                @change="r => defaultRules.splice(ind, 1, r)"
                @delete="defaultRules.splice(ind, 1)">
            </component>
        </div>
    </div>
</template>
<script>
    import packageRule from './packageRule'
    import genericRule from './genericRule'
    export default {
        name: 'artiTable',
        components: { packageRule, genericRule },
        props: {
            initData: {
                type: Array,
                default: () => []
            },
            disabled: {
                type: Boolean,
                default: false
            },
            scanType: String
        },
        data () {
            return {
                showAddBtn: false,
                defaultRules: []
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            ruleType () {
                switch (this.scanType) {
                    case 'MOBILE':
                        return 'generic-rule'
                    case 'DEPENDENT':
                        return 'package-rule'
                    default:
                        return ''
                }
            }
        },
        watch: {
            initData: {
                handler (data) {
                    this.defaultRules = data.map(r => ({ nameRule: r.nameRule || undefined, versionRule: r.versionRule || undefined }))
                    this.showAddBtn = Boolean(data.length)
                },
                immediate: true
            }
        },
        methods: {
            getConfig () {
                return new Promise((resolve, reject) => {
                    if (!this.showAddBtn) resolve([])
                    else {
                        const rules = this.defaultRules
                            .map(({ nameRule, versionRule }) => {
                                return {
                                    nameRule: nameRule?.value ? nameRule : null,
                                    versionRule: versionRule?.value ? versionRule : null
                                }
                            })
                            .filter(rule => Boolean(rule.nameRule || rule.versionRule))
                        rules.length ? resolve(rules) : reject(new Error())
                    }
                })
            },
            addRule () {
                this.defaultRules.push({})
            }
        }
    }
</script>
<style lang="scss" scoped>
.arti-table-container {
    ::v-deep .bk-form-radio {
        height: 32px;
        display: flex;
        align-items: center;
        .bk-radio-text {
            display: flex;
            align-items: center;
        }
    }
    .rule-list {
        &:before {
            content: '不填写则跳过规则';
            display: block;
            margin-bottom: -10px;
            color: var(--fontSubsidiaryColor);
        }
    }
    .rule-add {
        width: 120px;
        height: 32px;
        color: var(--primaryColor);
        background-color: var(--bgHoverColor);
        cursor: pointer;
    }
}
</style>
