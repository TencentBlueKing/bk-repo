<template>
    <div class="user-manage-container">
        <header class="user-manage-header">
            {{ $t('userManage') }}
            <bk-button theme="default" @click="$router.back()">
                {{$t('returnBack')}}
            </bk-button>
        </header>
        <main class="user-manage-main">
            <bk-tab class="user-manage-tab" type="unborder-card">
                <bk-tab-panel name="user" :label="$t('user')">
                    <User />
                </bk-tab-panel>
                <bk-tab-panel render-directive="if" name="userGroup" :label="$t('userGroup')">
                    <Role />
                </bk-tab-panel>
            </bk-tab>
        </main>
    </div>
</template>
<script>
    import User from './user'
    import Role from './role'
    export default {
        name: 'userManage',
        components: { User, Role }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.user-manage-container {
    height: 100%;
    .user-manage-header {
        height: 50px;
        padding: 0 20px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 14px;
        background-color: white;
    }
    .user-manage-main {
        height: calc(100% - 80px);
        margin-top: 20px;
        padding: 20px;
        background-color: white;
        border: 1px solid $borderWeightColor;
        .user-manage-tab {
            height: 100%;
            /deep/ .bk-tab-section {
                height: calc(100% - 40px);
                .bk-tab-content {
                    height: 100%;
                }
            }
        }
    }
}
</style>
