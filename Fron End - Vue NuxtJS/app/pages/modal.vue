<script setup>
const open = ref(false)

const hiAlert = () => {
  alert('salam')
  open.value = false
}
</script>

<template>
  <div>
    <UButton
      label="باز کردن"
      @click="open = true"
    />

    <UModal
      v-model:open="open"
      :dismissible="false"
      prevent-close
    >
      <template #content>
        <div class="p-6 flex flex-col gap-4">
          <h3 class="text-lg font-semibold">
            عنوان
          </h3>
          <p class="text-muted text-sm">
            مطمئنید حذف شود؟
          </p>

          <div class="flex justify-end gap-2 mt-4">
            <UButton
              label="بله"
              color="j2os-warning"
              @click="hiAlert"
            />
            <UButton
              label="بستن"
              color="neutral"
              variant="outline"
              @click="open = false"
            />
          </div>
        </div>
      </template>
    </UModal>
  </div>
</template>
