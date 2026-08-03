<script setup>
const toast = useToast()
const config = useRuntimeConfig()
const API_BASE = config.public.API_BASE
// خوبی استفاده از reactive اینه که نیازی به کلمه value برای دسترسی به instance وجود ندارد
const instanceTree = reactive(useTree({
  listUrl: `${API_BASE}/getTree`,
  parentIdParam: 'NODE_PARENT_ID',
  fields: {
    id: 'NODE_ID',
    label: 'NODE_NAME',
    hasChildren: 'NODE_HAS_CHILDREN'
  },
  onError: (error) => {
    toast.add({ title: 'خطا رخ داد', description: error.message, color: 'error' })
  },
  onNodeClick: (item) => {
    console.log('کلیک شد روی نود با شناسه:', item.id)
  },
  actions: [
    {
      key: 'add',
      label: 'افزودن فرزند',
      icon: 'i-lucide-plus-circle',
      color: 'text-green-500',
      hoverClass: 'hover:bg-green-50 dark:hover:bg-green-900/20',
      handler: onPersist
    },
    {
      key: 'view',
      label: 'مشاهده',
      icon: 'i-lucide-eye',
      color: 'text-blue-500',
      hoverClass: 'hover:bg-blue-50 dark:hover:bg-blue-900/20',
      keepSelection: true,
      handler: (item) => {
        navigateTo(`/categories/${item.id}`)
      }
    },
    {
      key: 'reload',
      label: 'بارگزاری مجدد',
      icon: 'i-lucide-refresh-ccw',
      color: 'text-yellow-500',
      hoverClass: 'hover:bg-red-50 dark:hover:bg-red-900/20',
      handler: onReload
    },
    {
      key: 'delete',
      label: 'حذف',
      icon: 'i-lucide-trash-2',
      color: 'text-red-500',
      hoverClass: 'hover:bg-red-50 dark:hover:bg-red-900/20',
      handler: onDelete
    }
  ]
}))

////////////////////////////////////////////////////////////////////
async function onReload() {
  await instanceTree.reset()
}

////////////////////////////////////////////////////////////////////
async function onDelete(item, tree) {
  const formData = new FormData()
  formData.append('NODE_ID', item.id)
  const res = await fetch(`${API_BASE}/removeTree`, {
    method: 'POST',
    body: formData
  })
  const serverTextResponse = await res.text()
  if (!res.ok) {
    throw new Error(serverTextResponse || 'سرور پاسخگو نیست و دچار اشکال شده است')
  }

  let result
  try {
    result = JSON.parse(serverTextResponse)
  } catch (error) {
    throw new Error('پاسخ سرور معتبر نیست', { cause: error })
  }

  await instanceTree.changeReload(tree, item, result.NODE_PARENT_ID)
  toast.add({ title: 'با موفقیت حذف شد', color: 'success' })
}

////////////////////////////////////////////////////////////////////
async function onPersist(item, tree) {
  const formData = new FormData()
  formData.append('NODE_PARENT_ID', item.id)
  const res = await fetch(`${API_BASE}/saveTree`, {
    method: 'POST',
    body: formData
  })
  if (!res.ok) throw new Error('خطا در افزودن')
  await instanceTree.addReload(tree, item)
  toast.add({ title: 'با موفقیت اضافه شد', color: 'success' })
}

onMounted(instanceTree.init)
</script>

<template>
  <div>
    <UPageCard title="درخت">
      <Tree
        v-model:expanded-keys="instanceTree.expandedKeys"
        v-model:menu="instanceTree.menu"
        :tree-data="instanceTree.treeData"
        :menu-x="instanceTree.menuX"
        :menu-y="instanceTree.menuY"
        :selected-item="instanceTree.selectedItem"
        :visible-actions="instanceTree.visibleActions"
        :is-busy="instanceTree.isBusy"
        :open-menu="instanceTree.openMenu"
        :run-action="instanceTree.runAction"
        :handle-node-click="instanceTree.handleNodeClick"
      />
    </UPageCard>
  </div>
</template>
