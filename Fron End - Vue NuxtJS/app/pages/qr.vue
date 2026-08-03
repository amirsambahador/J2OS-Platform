<script setup>
const qr = useQR()

onMounted(() => {
  qr.render('qrBox', 'http://j2os.org?item=10')
})

const download = () => {
  qr.download('qrBox')
}
</script>

<template>
  <div>
    <div id="qrBox" />
    <br>
    <div>
      <UButton @click="download">
        Download
      </UButton>
    </div>
  </div>
</template>
