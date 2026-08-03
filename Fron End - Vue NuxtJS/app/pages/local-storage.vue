<script setup>
const entityManager = useLocalStorage()
const onPersist = () => {
  entityManager.persist('name', 'amirsam')
  entityManager.persist('family', 'bahador')
  entityManager.persist('json', JSON.stringify({ language: 'java', country: 'iram' }))
}
const onShow = () => {
  alert(entityManager.findByKey('name'))
  alert(entityManager.findByKey('family'))
  const json = JSON.parse(entityManager.findByKey('json'))
  alert(json.language)
}

const onGetKeys = () => {
  for (const key of entityManager.getKeys()) {
    alert(key)
  }
}

const onRemove = () => {
  entityManager.removeByKey('name')
  entityManager.removeByKey('family')
  entityManager.removeByKey('json')
}
</script>

<template>
  <p>
    <UButton @click="onPersist">
      ذخیره
    </UButton>
    <br> <br>
    <UButton @click="onShow">
      نمایش رکورد
    </UButton>
    <br> <br>
    <UButton @click="onGetKeys">
      نمایش کلید ها
    </UButton>
    <br> <br>
    <UButton @click="onRemove">
      حذف
    </UButton>
  </p>
</template>
