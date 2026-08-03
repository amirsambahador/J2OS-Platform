<template>
  <div>
    وضعیت ذخیره شد، دقت کنید کاربر اگر مرورگر را رفرش کند یا از طریق لینکی که باعث رفرش می شود به صفحه دیگر برود وضعیت از
    دست می رود
    <br>
    نکته جالب دیگری وجود دارد! اگر کاربر با استفاده از route یا nuxtlink یا ulink به صفحه اولی که استیت مقدار داده شده برگردد، مقدار باز هم حفظ می شود و arrow function مربوط به state فراخوانی نمیشود
    <br>
    <ULink href="/state2">انتقال کاربر با ناکس لینک به صفحه state2</ULink>
  </div>
</template>

<script setup>
const state = useState('nameYekta', () => {
  return 0
})
onMounted(() => {
  state.value++
})
</script>
