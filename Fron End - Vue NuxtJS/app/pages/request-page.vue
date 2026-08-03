<script setup>
const router = useRouter()
const onClick = () => {
  router.push({
    path: '/response-page',
    query: { param1: 'amirsam' }
  })
}
</script>

<template>
  <UButton @click="onClick">
    اینجا request-page است بعد از کلیک به reponse-page منتقل می شوید
  </UButton>
</template>
