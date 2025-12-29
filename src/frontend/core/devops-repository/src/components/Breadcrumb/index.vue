<template>
    <bk-breadcrumb separator-class="bk-icon icon-angle-right">
        <slot></slot>
        <bk-breadcrumb-item
            v-for="item in list"
            :key="item.name"
            :class="{ 'omit-middle': omitMiddle && list && list.length > 2 }">
            <bk-popover :content="item.name" placement="bottom">
                <span
                    class="bk-breadcrumb-item-inner is-link"
                    @click="() => item.cilckHandler && item.cilckHandler(item)">
                    {{ item.name }}
                </span>
            </bk-popover>
        </bk-breadcrumb-item>
    </bk-breadcrumb>
</template>
<script>
    export default {
        name: 'breadcrumb',
        props: {
            list: {
                type: Array,
                default: () => []
            },
            // 是否需要在面包屑超过两个之后只显示首尾，中间显示省略号
            omitMiddle: {
                type: Boolean,
                default: false
            }

        }
    }
</script>
<style lang="scss" scoped>
.bk-breadcrumb-item.omit-middle{
    &:not(:first-child) {
        &:not(:last-child){
            display: none !important;
        }
    }
    &:first-child:after{
        content: '...>';
    }
}
</style>
