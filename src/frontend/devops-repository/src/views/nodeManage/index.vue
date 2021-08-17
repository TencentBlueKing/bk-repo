<template>
    <div class="node-manage-container">
        <header class="node-manage-header">
            {{ $t($route.name) }}
            <bk-button theme="default" @click="$router.back()">
                {{$t('returnBack')}}
            </bk-button>
        </header>
        <main class="node-manage-main">
            <Plan v-if="$route.name === 'planManage'" />
            <Node v-else-if="$route.name === 'nodeManage'" />
        </main>
    </div>
</template>
<script>
    import Node from './node'
    import Plan from './plan'
    export default {
        name: 'nodeManage',
        components: { Node, Plan }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.node-manage-container {
    height: 100%;
    .node-manage-header {
        height: 50px;
        padding: 0 20px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 14px;
        background-color: white;
    }
    .node-manage-main {
        height: calc(100% - 70px);
        margin-top: 20px;
        padding: 20px;
        background-color: white;
        .node-manage-tab {
            height: 100%;
            ::v-deep .bk-tab-section {
                height: calc(100% - 40px);
                .bk-tab-content {
                    height: 100%;
                }
            }
        }
    }
}
</style>
