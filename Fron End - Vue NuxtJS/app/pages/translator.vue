<template>
  <div>
    <UPageCard
      title="مترجم"
      variant="soft"
    >
      <UInput v-model="text" />
      <UButton @click="execute">
        مترجم
      </UButton>
      <div dir="ltr">
        {{ translatedContent }}
      </div>
    </UPageCard>
  </div>
</template>

<script setup>
const translator = useTranslatorAPI()
const text = ref()
const translatedContent = ref()

const execute = async () => {
  try {
    translatedContent.value = await translator.translate(text.value, 'fa', 'en')
  } catch (err) {
    translatedContent.value = ''
    alert(err.message)
  }
}
</script>
