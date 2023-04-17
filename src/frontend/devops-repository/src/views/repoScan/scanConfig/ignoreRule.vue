<template>
    <div>
        <div>
            <bk-button style="margin-bottom: 10px" theme="primary" @click="dialogVisible = true">{{ $t('create') }}</bk-button>
        </div>
        <bk-table
            :data="ignoreRules"
            :pagination="pagination"
            size="small">
            <bk-table-column :label="$t('name')" width="120" prop="name"></bk-table-column>
            <bk-table-column :label="$t('description')" width="120" prop="description"></bk-table-column>
            <bk-table-column :label="$t('repoName')" width="120" prop="repoName"></bk-table-column>
            <bk-table-column :label="$t('path')" width="120" prop="fullPath"></bk-table-column>
            <bk-table-column :label="$t('packageName')" width="120" prop="packageKey"></bk-table-column>
            <bk-table-column :label="$t('version')" width="120" prop="packageVersion"></bk-table-column>
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

    export default {
        name: 'ignoreRule',
        components: { CreateOrUpdateIgnoreRuleDialog },
        props: {
            projectId: String,
            planId: String
        },
        data () {
            return {
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
                'getIgnoreRules'
            ]),
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
