<template>
    <bk-popover
        :class="{ 'operation-trigger': !Object.keys($slots).length }"
        :style="list.length ? '' : 'display:none;'"
        placement="bottom-end"
        theme="light"
        ext-cls="operation-container"
        v-bind="$attrs">
        <slot>
            <i @click.stop="() => {}" class="devops-icon icon-more flex-center"></i>
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
    width: 24px;
    height: 24px;
    border-radius: 50%;
    cursor: pointer;
    .bk-tooltip-ref,
    .icon-more {
        width: 100%;
        height: 100%;
    }
    &:hover {
        background-color: var(--bgHoverLighterColor);
    }
}
</style>
