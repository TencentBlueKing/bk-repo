<template>
    <bk-popover
        :class="{ 'operation-trigger': !Object.keys($slots).length }"
        :style="list.length ? '' : 'display:none;'"
        placement="bottom-end"
        theme="light"
        ext-cls="operation-container"
        v-bind="$attrs">
        <slot>
            <i @click.stop="() => {}" class="devops-icon icon-more flex-center hover-btn"></i>
        </slot>
        <template #content><ul class="operation-list">
            <li v-for="li in list" :key="li.label"
                class="operation-item hover-btn"
                :class="{ 'disabled': li.disabled }"
                @click.stop="li.clickEvent()">
                {{ li.label }}
            </li>
        </ul></template>
    </bk-popover>
</template>
<script>
    export default {
        name: 'operationList',
        props: {
            list: {
                type: Array,
                default: () => []
            }
        }
    }
</script>
<style lang="scss">
.operation-trigger {
    border-radius: 2px;
    cursor: pointer;
    .icon-more {
        padding: 3px;
        font-size: 18px;
    }
}
</style>
