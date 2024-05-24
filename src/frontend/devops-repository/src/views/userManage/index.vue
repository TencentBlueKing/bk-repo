<template>
    <div class="user-manage-container">
        <bk-tab class="user-manage-tab page-tab" type="unborder-card" :active.sync="tabName">
            <bk-tab-panel name="user" :label="$t('user')">
                <User />
            </bk-tab-panel>
            <bk-tab-panel render-directive="if" name="userGroup" :label="$t('userGroup')">
                <Role />
            </bk-tab-panel>
        </bk-tab>
    </div>
</template>
<script>
    import User from './user'
    import Role from '@repository/views/userGroup/index'
    export default {
        name: 'userManage',
        components: { User, Role },
        data () {
            return { tabName: 'user' }
        }
    }
</script>
<style lang="scss" scoped>
.user-manage-container {
    height: 100%;
    background-color: white;
    .user-manage-tab {
        height: 100%;
        ::v-deep .bk-tab-section {
            height: calc(100% - 60px);
            padding: 0;
            .bk-tab-content {
                height: 100%;
                overflow: hidden;
            }
        }
    }
}
</style>
