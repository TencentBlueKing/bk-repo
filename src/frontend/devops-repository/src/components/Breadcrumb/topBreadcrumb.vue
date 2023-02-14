<template>
    <bk-breadcrumb separator-class="bk-icon icon-angle-right">
        <slot></slot>
        <bk-breadcrumb-item
            v-for="item in list"
            :key="item.name"
            :to="{ name: item.name, params: { ...$route.query, ...$route.params }, query: $route.query }">
            {{ transformLabel($t(item.name)) || item.template }}
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
                const ctx = { ...this.$route.params, ...this.$route.query }
                const transformLabel = label.replace(/\{(.*?)\}/g, (_, $1) => {
                    return $1 in ctx ? ctx[$1] : ''
                })
                return this.replaceRepoName(transformLabel)
            }
        }
    }
</script>
