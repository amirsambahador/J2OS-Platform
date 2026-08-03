<template>
  <div>
    <div style="display: flex; gap: 8px; padding: 8px; flex-wrap: wrap;">
      <UButton @click="console.log(editor1.get())">
        Get
      </UButton>
      <UButton @click="handleSet">
        Set
      </UButton>
      <UButton @click="editor1.downloadCode('Main.java')">
        دانلود فایل
      </UButton>
      <UButton @click="editor1.readOnly()">
        خواندنی
      </UButton>
      <UButton @click="editor1.readWrite()">
        نوشتنی
      </UButton>
      <UButton @click="editor1.reformatCode()">
        فرمت
      </UButton>
    </div>

    <div
      id="editor1"
      style="height: 500px;"
    />
  </div>
</template>

<script setup>
const editor1 = useCodeEditor()

const javaSnippets = [
  {
    name: 'psvm',
    tabTrigger: 'psvm',
    content: `public static void main(String[] args) {}`
  },
  {
    name: 'sout',
    tabTrigger: 'sout',
    content: `System.out.println();`
  }
]

const jpaAutocompletes = [
  { caption: '@OneToMany', value: 'OneToMany', meta: 'JPA Annotation', score: 1000 },
  { caption: '@ManyToOne', value: 'ManyToOne', meta: 'JPA Annotation', score: 1000 },
  { caption: '@Entity', value: 'Entity', meta: 'JPA Annotation', score: 1000 },
  { caption: '@Table', value: 'Table(name = "")', meta: 'JPA Annotation', score: 1000 }
]

function handleSet() {
  editor1.set('System.out.println("سلام");')
}

onMounted(async () => {
  await editor1.init('editor1', {
    language: 'javascript',
    snippets: javaSnippets,
    autocompletes: jpaAutocompletes
  })
})

onBeforeUnmount(() => {
  editor1.destroy()
})
</script>
