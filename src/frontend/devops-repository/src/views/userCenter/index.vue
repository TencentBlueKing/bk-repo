<template>
    <div class="user-center-container">
        <header class="user-center-header">
            {{ $t('userCenter') }}
            <bk-button theme="default" @click="$router.back()">
                {{$t('returnBack')}}
            </bk-button>
        </header>
        <main class="user-center-main">
            <bk-tab class="user-manage-tab" type="unborder-card">
                <bk-tab-panel name="userInfo" :label="$t('userInfo')">
                    <user-info />
                </bk-tab-panel>
                <bk-tab-panel render-directive="if" name="token" :label="$t('token')">
                    <repo-token />
                </bk-tab-panel>
            </bk-tab>
        </main>
    </div>
</template>
<script>
    import repoToken from '../repoToken'
    import userInfo from './userInfo'
    export default {
        name: 'userCenter',
        components: { userInfo, repoToken }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.user-center-container {
    height: 100%;
    .user-center-header {
        height: 50px;
        padding: 0 20px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 14px;
        background-color: white;
    }
    .user-center-main {
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
