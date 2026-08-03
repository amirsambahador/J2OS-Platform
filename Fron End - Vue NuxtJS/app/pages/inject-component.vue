<template>
  <InjectableComponent>
    <template #header>
      <h1>این متن در هدر کامپوننت قرار میگیرد</h1>
    </template>

    <template #sidebar>
      این در اسلاید قرار میگیرد
    </template>

    <template #default>
      محتوای اصلی
    </template>
  </InjectableComponent>
</template>
