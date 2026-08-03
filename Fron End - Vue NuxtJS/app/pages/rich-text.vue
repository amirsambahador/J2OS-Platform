<template>
  <div class="w-full lg:w-1/2">
    <div style="display: flex; gap: 8px; padding: 8px; flex-wrap: wrap;">
      <UButton @click="editor1.setHtml('<p>سلام</p>')">
        SET HTML 1
      </UButton>
      <UButton @click="console.log(editor1.getText())">
        GET TEXT 1 (CONSOLE)
      </UButton>
      <UButton @click="console.log(editor1.getHtml())">
        GET HTML 1 (CONSOLE)
      </UButton>
      <UButton @click="editor1.exportHTML('amirsam.html')">
        EXPORT HTML 1
      </UButton>
      <UButton @click="editor1.exportText('bahador.txt')">
        EXPORT TEXT 1
      </UButton>
      <UButton @click="sendData">
        SEND DATA 1
      </UButton>
      <UButton @click="getData">
        GET DATA 1
      </UButton>
      <UButton @click="editor1.setReadOnly(true)">
        READ ONLY
      </UButton>
      <UButton @click="editor1.setReadOnly(false)">
        READ WRITE
      </UButton>
      <br> <br>
    </div>
    <textarea id="editorId" />
  </div>
</template>

<script setup>
const config = useRuntimeConfig()
const API_BASE = config.public.API_BASE
const editor1 = useRichText()

onMounted(async () => {
  await editor1.init('editorId')
})

onBeforeUnmount(() => {
  editor1.destroy()
})

const sendData = async () => {
  const formData = new FormData()
  formData.append('t1', editor1.getHtml())
  const response = await fetch(`${API_BASE}/setHtml`, {
    method: 'POST',
    body: formData
  })
  alert(await response.text())
}

const getData = async () => {
  const response = await fetch(`${API_BASE}/getHtml`, {
    method: 'GET'
  })
  const result = await response.text()
  console.log(result)
  editor1.setHtml(result)
}
</script>
