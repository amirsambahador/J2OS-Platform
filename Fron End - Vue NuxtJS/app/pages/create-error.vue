<script setup>
const execute = () => {
  showError({
    statusCode: 900,
    statusMessage: 'خطای سفارشی'
  })
}
</script>

<template>
  <div>
    در ناکس هر خطایی بوجود آید، به صفحه error بیرون از فولدر پپیج کاربر هدایت می شود مثل: 404، با زدن این دکمه نیز خطای
    900 که یک خطای سفارشی است ایجاد می شود
    <UButton @click="execute">
      ایجاد خطا
    </UButton>
  </div>
</template>
