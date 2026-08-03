<template>
  <div>
    دریافت شد
    <br>
    <ULink href="/state1">انتقال کاربر با ناکس لینک به صفحه state1</ULink>
  </div>
</template>

<script setup>
const state = useState('nameYekta')

onMounted(() => {
  alert(state.value)
})
</script>
