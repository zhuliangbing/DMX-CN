<template>
  <div class="dm5-icon-picker">
    <div v-if="infoMode" class="fa icon">{{object.value}}</div>
    <div v-else>
      <el-button @click="open" class="fa icon">{{object.value}}</el-button>
      <el-dialog :visible.sync="visible">
        <fa-search @icon-select="select"></fa-search>
      </el-dialog>
    </div>
  </div>
</template>

<script>
export default {

  mixins: [
    require('./mixins/object').default,       // object to render
    require('./mixins/mode').default,
    require('./mixins/info-mode').default
  ],

  data () {
    return {
      visible: false      // dialog visibility
    }
  },

  methods: {

    open () {
      this.visible = true
    },

    close () {
      this.visible = false
    },

    select (icon) {
      console.log('选择图标', icon.id, icon.unicode)
      this.object.value = String.fromCharCode(parseInt(icon.unicode, 16))
      this.close()
    }
  },

  components: {
    'fa-search': () => ({
      component: import('vue-font-awesome-search' /* webpackChunkName: "fa-search" */),
      loading: require('./dm5-spinner')
    })
  }
}
</script>

<style>
.dm5-icon-picker .icon {
  font-size: 24px !important;
  color: var(--color-topic-icon);
}
</style>
