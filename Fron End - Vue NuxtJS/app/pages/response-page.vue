<script setup>
const route = useRoute()
const onClick = () => {
  alert(route.query['param1'])
}
</script>

<template>
  <div>
    <UButton @click="onClick">
      نمایش پارامتر
    </UButton>
    <br>
    <br>
    <UButton to="/request-page">
      بازگشت به صفحه در خواست
    </UButton>
  </div>
</template>
