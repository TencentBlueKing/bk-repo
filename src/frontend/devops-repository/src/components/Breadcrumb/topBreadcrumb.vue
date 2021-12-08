<template>
    <bk-breadcrumb separator-class="bk-icon icon-angle-right">
        <slot></slot>
        <bk-breadcrumb-item
            v-for="item in list"
            :key="item.name"
            :to="{ name: item.name, params: $route.params, query: $route.query }">
            {{ transformLabel(item.label) || item.template }}
        </bk-breadcrumb-item>
    </bk-breadcrumb>
</template>
<script>
    export default {
        name: 'topBreadCrumb',
        computed: {
            list () {
                return this.$route.matched.map(r => r.meta.breadcrumb || []).flat(Infinity)
            }
        },
        methods: {
            transformLabel (label) {
                // eslint-disable-next-line no-new-func
                const transform = new Function(
                    'ctx',
                    `return '${label.replace(/\{(.*?)(\?){0,1}\}/g, '\'\+ (ctx.hasOwnProperty(\'$1\') ? ctx[\'$1\'] : "") \+\'')}'`
                )
                const transformLabel = transform({ ...this.$route.params, ...this.$route.query })
                return this.replaceRepoName(transformLabel)
            }
        }
    }
</script>
