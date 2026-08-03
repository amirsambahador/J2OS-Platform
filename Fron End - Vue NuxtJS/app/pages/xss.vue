<template>
  <div>
    <UButton @click="xssProtection">
      نمایش
    </UButton>
    <br> <br>
    protectStrictXSS:
    <br>
    {{ output1 }}
    <br> <br>
    protectXSS:
    <br>
    {{ output2 }}
  </div>
</template>

<script setup>
const security = useSecurity()
const output1 = ref()
const output2 = ref()
const xssProtection = () => {
  output1.value = security.protectStrictXSS(`<button style="background: #0A5331" onclick="alert('salam')">TEST</button>`)
  output2.value = security.protectXSS(`<button style="background: #0A5331" onclick="alert('salam')">TEST</button>`)
}
</script>
