<script setup>
/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */

const props = defineProps({
  url: {
    type: String,
    required: true
  },
  columns: {
    type: Array,
    required: true
  },
  pageSize: {
    type: Number,
    default: 10
  },
  defaultSort: {
    type: String,
    required: true
  },
  rowKey: {
    type: String,
    default: 'id'
  },
  emptyText: {
    type: String,
    default: 'داده‌ای وجود ندارد'
  },
  rowStyle: {
    type: Function,
    default: () => ''
  }
})

const emit = defineEmits(['selected', 'error'])

const error = ref(null)
const rows = ref([])
const total = ref(0)
const loading = ref(true)
const page = ref(1)
const search = ref('')
const sort = ref(props.defaultSort)
const order = ref('ASC')
const rowSelection = ref({})
const radioUniqueId = useId()

let controller = null

async function loadData() {
  if (!props.url) {
    controller?.abort()
    controller = null

    rows.value = []
    total.value = 0
    loading.value = false
    error.value = null

    return
  }

  controller?.abort()

  const currentController = new AbortController()
  controller = currentController

  loading.value = true

  try {
    const params = new URLSearchParams({
      page: String(page.value),
      rows: String(props.pageSize),
      q: search.value,
      sort: sort.value,
      order: order.value
    })

    const response = await fetch(
      `${props.url}${params}`,
      {
        signal: currentController.signal
      }
    )
    //with token
    /**
     const token = entityManager.findByKey('token')
     const response = await fetch(`${props.url}${params}`, {
     signal: currentController.signal,
     headers: {
     ...(token && { Authorization: `Bearer ${token}` })
     }
     })
     **/

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    const data = await response.json()

    if (controller !== currentController) {
      return
    }

    rows.value = Array.isArray(data.rows)
      ? data.rows
      : []

    total.value = Number(data.total) || 0
    error.value = null
  } catch (e) {
    if (e.name !== 'AbortError') {
      console.error(e)

      if (controller === currentController) {
        error.value = e
        rows.value = []
        total.value = 0
        emit('error', e)
      }
    }
  } finally {
    if (controller === currentController) {
      loading.value = false
    }
  }
}

function reload() {
  rowSelection.value = {}
  emit('selected', null)
  loadData()
}

defineExpose({
  reload
})

function changeSort(col) {
  if (!col.sortable) {
    return
  }

  const nextSort = col.sortName || col.field

  order.value
    = sort.value === nextSort && order.value === 'ASC'
      ? 'DESC'
      : 'ASC'

  sort.value = nextSort

  if (page.value !== 1) {
    page.value = 1
  } else {
    loadData()
  }
}

function onSelect(e, row) {
  rowSelection.value = {
    [row.id]: true
  }

  emit('selected', row.original)
}

const availableFieldNames = computed(() => {
  const fieldNameSet = new Set()

  for (const row of rows.value) {
    for (const fieldName of Object.keys(row)) {
      fieldNameSet.add(fieldName)
    }
  }

  return fieldNameSet
})

const tableColumns = computed(() => [
  {
    id: 'select',
    header: 'گزینه',

    cell: ({ row }) =>
      h(
        'div',
        {
          class: 'px-6 py-3',
          style: props.rowStyle(row.original)
        },
        h('input', {
          name: radioUniqueId,
          type: 'radio',

          checked: !!rowSelection.value[row.id],

          onClick: (event) => {
            event.stopPropagation()
          },

          onChange: (event) => {
            onSelect(event, row)
          }
        })
      )
  },

  ...props.columns
    .filter(col =>
      availableFieldNames.value.has(col.field)
    )
    .map(col => ({
      id: col.sortName || col.field,
      accessorKey: col.field,

      header: () =>
        h(
          'button',
          {
            disabled: !col.sortable,
            onClick: () => changeSort(col)
          },
          col.label
        ),

      cell: ({ row }) => {
        const value = col.processor
          ? col.processor(row.original[col.field])
          : row.original[col.field]

        const cellProps = {
          class: 'px-4 py-3',
          style: props.rowStyle(row.original)
        }

        if (col.render) {
          return h(
            'div',
            {
              ...cellProps,
              innerHTML: value
            }
          )
        }

        return h(
          'div',
          cellProps,
          value
        )
      }
    }))
])

let skipPageLoad = false
let searchTimer

watch(page, () => {
  if (skipPageLoad) {
    skipPageLoad = false
    return
  }

  loadData()
})

watch(search, () => {
  clearTimeout(searchTimer)

  if (page.value !== 1) {
    skipPageLoad = true
    page.value = 1
  }

  searchTimer = setTimeout(loadData, 500)
})

watch(
  () => props.url,
  () => {
    rowSelection.value = {}
    emit('selected', null)

    if (page.value !== 1) {
      page.value = 1
      return
    }

    loadData()
  }
)

onMounted(loadData)

onUnmounted(() => {
  clearTimeout(searchTimer)
  controller?.abort()
})
</script>

<template>
  <div class="space-y-4">
    <div class="flex">
      <div class="flex gap-3">
        <slot name="before-search" />

        <UInput
          v-model="search"
          placeholder="جستجو..."
        />

        <slot name="after-search" />
      </div>
    </div>

    <div
      v-if="loading"
      class="flex items-center justify-center gap-3 py-10 text-gray-400"
    >
      <UIcon
        name="i-lucide-loader-circle"
        class="size-5 animate-spin"
      />

      <span>
        در حال بارگذاری...
      </span>
    </div>

    <div
      v-else-if="error"
      class="flex flex-col items-center justify-center gap-2 py-10 text-yellow-400"
    >
      <UIcon
        name="i-lucide-circle-alert"
        class="size-8"
      />

      <span>
        خطا در دریافت اطلاعات وجود دارد
      </span>
    </div>

    <div
      v-else-if="!rows.length"
      class="flex flex-col items-center justify-center gap-2 py-10 text-gray-400"
    >
      <UIcon
        name="i-lucide-folder-x"
        class="size-8"
      />

      <span>
        {{ emptyText }}
      </span>
    </div>

    <UTable
      v-else
      v-model:row-selection="rowSelection"
      :data="rows"
      :columns="tableColumns"
      :get-row-id="row => String(row[rowKey])"
      :ui="{
        th: 'bg-accented',
        tr: 'odd:bg-elevated even:bg-default',
        tbody: 'divide-y-0',
        td: 'p-0'
      }"
      class="border rounded-lg"
      style="border-color: var(--ui-border-accented)"
      @select="onSelect"
    />

    <div class="flex justify-center">
      <UPagination
        v-model:page="page"
        :total="total"
        :items-per-page="pageSize"
        :sibling-count="2"
      />
    </div>
  </div>
</template>
