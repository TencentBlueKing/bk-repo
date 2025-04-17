<template>
    <div>
        <div>
            <bk-button style="margin-bottom: 10px" theme="primary" @click="edit(null)">{{ $t('create') }}</bk-button>
        </div>
        <bk-table
            :data="ignoreRules"
            :pagination="pagination"
            size="small">
            <bk-table-column :show-overflow-tooltip="true" :label="$t('name')" width="200" prop="name"></bk-table-column>
            <bk-table-column :show-overflow-tooltip="true" :label="$t('ruleIgnoreRuleType')" width="80" prop="ruleType">
                <template slot-scope="props">
                    {{ props.row.type === FILTER_RULE_IGNORE ? $t('ruleIgnoreIfMatch') : $t('ruleIgnoreIfNotMatch')}}
                </template>
            </bk-table-column>
            <bk-table-column :show-overflow-tooltip="true" :label="$t('description')" width="240" prop="description"></bk-table-column>
            <bk-table-column :show-overflow-tooltip="true" :label="$t('repoName')" width="120" prop="repoName"></bk-table-column>
            <bk-table-column :show-overflow-tooltip="true" :label="$t('path')" width="200" prop="fullPath"></bk-table-column>
            <bk-table-column :show-overflow-tooltip="true" :label="$t('packageName')" width="200" prop="packageKey"></bk-table-column>
            <bk-table-column :show-overflow-tooltip="true" :label="$t('version')" width="200" prop="packageVersion"></bk-table-column>
            <bk-table-column :label="$t('operation')" width="120">
                <template slot-scope="props">
                    <bk-button class="mr10" theme="primary" text @click="edit(props.row)">{{ $t('edit') }}</bk-button>
                    <bk-button class="mr10" theme="primary" text @click="remove(props.row)">{{ $t('delete') }}</bk-button>
                </template>
            </bk-table-column>
        </bk-table>
        <create-or-update-ignore-rule-dialog
            :project-id="projectId"
            :plan-id="planId"
            :updating-rule="updatingRule"
            :visible.sync="dialogVisible"
            @success="onCreateOrUpdateSuccess"
        ></create-or-update-ignore-rule-dialog>
    </div>
</template>
<script>
    import { mapActions } from 'vuex'
    import CreateOrUpdateIgnoreRuleDialog from './createOrUpdateIgnoreRuleDialog'
    import { FILTER_RULE_IGNORE } from 'devops-repository/src/store/publicEnum'

    export default {
        name: 'ignoreRule',
        components: { CreateOrUpdateIgnoreRuleDialog },
        props: {
            projectId: String,
            planId: String
        },
        data () {
            return {
                FILTER_RULE_IGNORE: FILTER_RULE_IGNORE,
                dialogVisible: false,
                updatingRule: null,
                isLoading: false,
                ignoreRules: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                }
            }
        },
        created () {
            this.refresh()
        },
        methods: {
            ...mapActions([
                'getIgnoreRules', 'deleteIgnoreRule'
            ]),
            remove (rule) {
                this.deleteIgnoreRule({
                    projectId: this.projectId,
                    ruleId: rule.id
                }).then(_ => {
                    this.refresh()
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('delete') + this.$t('space') + this.$t('success')
                    })
                })
            },
            edit (rule) {
                this.updatingRule = rule
                this.dialogVisible = true
            },
            onCreateOrUpdateSuccess () {
                this.dialogVisible = false
                this.refresh()
            },
            refresh () {
                this.isLoading = true
                return this.getIgnoreRules({
                    projectId: this.projectId,
                    planId: this.planId,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.ignoreRules = records
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            }
        }
    }
</script>
