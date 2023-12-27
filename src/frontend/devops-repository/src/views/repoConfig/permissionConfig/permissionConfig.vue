<template>
    <div class="permission-container" v-bkloading="{ isLoading }">
        <div class="mb10 flex-between-center">
            <bk-button icon="plus" theme="primary" @click="showCreatePermission">{{ $t('add') }}</bk-button>
        </div>
        <div class="permission-head">
            <div class="permission-name">{{$t('name')}}</div>
            <div class="permission-path">{{$t('filePath')}}</div>
            <div class="permission-users">{{$t('associatedUser')}}</div>
            <div class="permission-operation">{{$t('operation')}}</div>
        </div>
        <draggable v-if="permissionListPages.length" v-model="permissionListPages" :options="{ animation: 200 }">
            <div class="proxy-item" v-for="(row,index) in permissionListPages" :key="index">
                <div class="permission-name">{{row.permName}}</div>
                <div class="permission-path"><bk-tag v-for="(name,pathIndex) in row.includePattern" :key="pathIndex">{{ changePath(name) }}</bk-tag></div>
                <div class="permission-users"><bk-tag v-for="(name,userIndex) in row.users" :key="userIndex">{{ name }}</bk-tag></div>
                <div class="flex-align-center permission-operation">
                    <Icon class="mr10 hover-btn" size="24" name="icon-edit" @click.native.stop="updatePermission(row)" />
                    <Icon class="hover-btn" size="24" name="icon-delete" @click.native.stop="deletePerm(row)" />
                </div>
            </div>
        </draggable>
        <empty-data :is-loading="isLoading" v-if="!permissionListPages.length"></empty-data>
        <create-permission :show="showCreatePermissionDialog"
            :type="showType"
            :permission-form="permissionDate"
            ref="createDialog"
            @refresh="refresh"
        > </create-permission>
    </div>
</template>
<script>
    import { mapActions } from 'vuex'
    import createPermission from '@/views/repoConfig/permissionConfig/createPermission'
    export default {
        name: 'permissionConfig',
        components: { createPermission },
        data () {
            return {
                isLoading: false,
                showCreatePermissionDialog: false,
                permissionListPages: [],
                repoInfo: '',
                showType: '',
                permissionDate: {
                    users: [],
                    includePattern: [],
                    name: ''
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
        watch: {
            baseData () {
                this.repoInfo = this.baseData
            }
        },
        created () {
            this.listPermissionDeployInRepo({
                projectId: this.projectId,
                repoName: this.repoName
            }).then(res => {
                this.permissionListPages = res
            })
        },
        methods: {
            ...mapActions([
                'listPermissionDeployInRepo',
                'deletePermission'
            ]),
            deletePerm (row) {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('deletePermissionMsg'),
                    confirmFn: () => {
                        return this.deletePermission({ id: row.id }).then(() => {
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('space') + this.$t('success')
                            })
                            this.refresh()
                        })
                    }
                })
            },
            updatePermission (row) {
                this.$refs.createDialog.show = true
                this.showType = 'update'
                this.permissionDate = {
                    id: row.id,
                    name: row.permName,
                    users: row.users,
                    includePattern: row.includePattern
                }
            },
            showCreatePermission () {
                this.$refs.createDialog.show = true
                this.showType = 'create'
                this.permissionDate = {
                    id: [],
                    name: '',
                    users: [],
                    includePattern: []
                }
            },
            refresh () {
                this.listPermissionDeployInRepo({
                    projectId: this.projectId,
                    repoName: this.repoName
                }).then(res => {
                    this.permissionListPages = res
                })
            },
            changePath (path) {
                if (path.length > 18) {
                    return path.substr(0, 15) + '...'
                } else {
                    return path
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
.permission-container {
    .proxy-item,
    .permission-head {
        display: flex;
        align-items: center;
        height: auto;
        min-height: 40px;
        border-bottom: 1px solid var(--borderColor);
        .permission-index {
            flex-basis: 50px;
        }
        .permission-name {
            flex:1;
        }
        .permission-path {
            flex: 4;
        }
        .permission-users {
            flex: 4;
        }
        .permission-operation {
            flex:1;
        }
    }
    .permission-head {
        color: var(--fontSubsidiaryColor);
        background-color: var(--bgColor);
    }
    .proxy-item {
        cursor: move;
    }
}
</style>
