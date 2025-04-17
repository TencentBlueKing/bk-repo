<template>
    <svg
        :width="size"
        :height="size"
        style="fill: currentColor"
    >
        <use v-bind="{ 'xlink:href': `#${name}` }" />
    </svg>
</template>

<script>
    export default {
        name: 'icon',
        props: {
            size: {
                type: [Number, String],
                default: 32
            },
            name: {
                type: String
            }
        }
    }
</script>
