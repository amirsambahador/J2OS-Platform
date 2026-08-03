<script setup>
const prefixNameForFunction = useFunction()
const prefixNameForClass = useClass()
onMounted(() => {
  prefixNameForFunction.f1()
  const amir = new prefixNameForClass.Amirsam()
  amir.setName('hamed')
  amir.setFamily('hamedi')
  alert(amir.getName() + ' ' + amir.getFamily() + ' ' + prefixNameForClass.Amirsam.AGE)
  amir.m1()
  prefixNameForClass.Amirsam.m2()
})
</script>

<template>
  <UPageCard
    title="تست کامپوزیبل"
    variant="soft"
  >
    <form>
      <UButton
        type="button"
        @click="prefixNameForFunction.f1()"
      >
        Submit
      </UButton>
    </form>
  </UPageCard>
</template>
