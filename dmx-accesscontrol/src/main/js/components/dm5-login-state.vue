<template>
  <div class="login-state">
    <div v-if="username" class="info">
       <span class="username">{{username}}成功登录</span>
    </div>
    <el-button type="text" @click="dispatch">{{buttonLabel}}</el-button>
  </div>
</template>

<script>
export default {

  computed: {

    username () {
      return this.$store.state.accesscontrol.username
    },

    buttonLabel () {
      return this.username ? '退出' : '登录'
    },

    buttonAction () {
      return this.username ? 'logout' : 'openLoginDialog'
    }
  },

  methods: {
    dispatch () {
      this.$store.dispatch(this.buttonAction)
    }
  }
}
</script>

<style>
.login-state {
  text-align: right;
}

.login-state .info {
  font-size: var(--label-font-size);
  color:     var(--label-color);
}

.login-state .info .username {
  color: black;
}

.login-state .el-button {
  font-size: var(--label-font-size) !important;
  padding: 0 !important;
}
</style>
