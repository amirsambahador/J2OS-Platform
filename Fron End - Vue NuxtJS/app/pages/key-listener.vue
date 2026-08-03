<script setup>
const keyListener = useKeyListener()

onMounted(() => {
  const removeListeners = keyListener.setupKeyBoardShortcuts({
    'F2': () => {
      console.log('F2 فشرده شد')
    },
    'Ctrl+s': (event) => {
      event.preventDefault()
      console.log('ذخیره شد')
    },
    'Ctrl+Shift+z': () => {
      console.log('redo')
    }
  })

  onUnmounted(() => removeListeners())
})
</script>

<template>
  <P>
    لاگ کنسول بررسی شود، کلید های فعال در این مثال عبارتند از:
    <BR />
    'F2'
    <BR />
    'Ctrl+s'
    <BR />
    'Ctrl+Shift+z' (حالا چه CapsLock روشن باشه چه نه، با حرف کوچک کار می‌کنه)
  </P>
</template>
