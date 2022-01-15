<template>
    <bk-breadcrumb separator-class="bk-icon icon-angle-right">
        <slot></slot>
        <bk-breadcrumb-item
            v-for="item in list"
            :key="item.name">
            <span class="bk-breadcrumb-item-inner is-link"
                @click="() => item.cilckHandler && item.cilckHandler(item)">
                {{ item.name }}
            </span>
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
            }
        }
    }
</script>
