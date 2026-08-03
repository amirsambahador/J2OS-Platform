<script setup>
const pageMethod1 = () => {
  alert('page method1!')
}
const pageMethod2 = () => {
  alert('page method2!')
}
</script>

<template>
  <InvokeFunctionComponent
    @page-method1="pageMethod1"
    @page-method2="pageMethod2"
  />
</template>
