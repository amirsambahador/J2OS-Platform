/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export function useTree(options) {
  const {
    listUrl,
    parentIdParam = 'parentId',
    fields = {},
    actions = [],
    onNodeClick = null,
    onError = null
  } = options

  const fieldMap = {
    id: 'id',
    label: 'label',
    hasChildren: 'hasChildren',
    ...fields
  }

  function reportError(error, context) {
    console.error(error)
    if (typeof onError === 'function') {
      onError(error, context)
    }
  }

  // ==================== State ====================

  const expandedKeys = ref([])
  const treeData = ref([])
  const loadedCache = new Map()
  const nodeIndex = new Map()
  const pendingLoads = new Map()

  const menu = ref(false)
  const menuX = ref(0)
  const menuY = ref(0)
  const selectedItem = ref(null)

  const busyIds = ref(new Set())

  function isBusy(id) {
    return busyIds.value.has(id)
  }

  function setBusy(id, val) {
    const next = new Set(busyIds.value)
    if (val) {
      next.add(id)
    } else {
      next.delete(id)
    }
    busyIds.value = next
  }

  // ==================== Core: ساخت نود و لود ====================

  function makeNode(item, parent) {
    const id = item[fieldMap.id]

    const node = reactive({
      id: id,
      label: item[fieldMap.label],
      loaded: false,
      parent: parent || null,
      raw: item
    })

    nodeIndex.set(String(id), node)

    if (item[fieldMap.hasChildren]) {
      if (loadedCache.has(id)) {
        node.children = loadedCache.get(id)
        node.loaded = true
      } else {
        node.children = [{ id: '__dummy__', label: '', _dummy: true }]

        node.onToggle = async function () {
          if (node.loaded) return

          if (pendingLoads.has(id)) {
            await pendingLoads.get(id)
            return
          }

          const loadPromise = loadChildren(id)
          pendingLoads.set(id, loadPromise)

          try {
            const children = await loadPromise
            loadedCache.set(id, children)
            node.children = children
            node.loaded = true
          } catch (e) {
            reportError(e, { phase: 'lazyLoad', node })
          } finally {
            pendingLoads.delete(id)
          }
        }
      }
    }

    return node
  }

  async function loadChildren(parentId) {
    const url = parentId
      ? `${listUrl}?${parentIdParam}=${parentId}`
      : listUrl

    const response = await fetch(url)
    if (!response.ok) throw new Error('خطا در دریافت اطلاعات')

    const data = await response.json()
    return data.map(function (item) {
      return makeNode(item, parentId ? { id: parentId } : null)
    })
  }

  async function refreshNode(node) {
    if (!node) return
    loadedCache.delete(node.id)
    const children = await loadChildren(node.id)
    loadedCache.set(node.id, children)
    node.children = children
    node.loaded = true
  }

  function findNodeById(id) {
    return nodeIndex.get(String(id)) || null
  }

  function purgeSubtree(node) {
    if (!node) return
    nodeIndex.delete(String(node.id))
    loadedCache.delete(node.id)
    if (Array.isArray(node.children)) {
      for (let i = 0; i < node.children.length; i++) {
        const child = node.children[i]
        if (!child._dummy) purgeSubtree(child)
      }
    }
  }

  async function reload() {
    nodeIndex.clear()
    loadedCache.clear()
    treeData.value = await loadChildren()
  }

  // ==================== Lifecycle ====================

  async function init() {
    try {
      treeData.value = await loadChildren()
    } catch (e) {
      reportError(e, { phase: 'init' })
    }
  }

  // ==================== Context Menu ====================

  function openMenu(e, item) {
    if (item._dummy) return
    e.preventDefault()
    e.stopPropagation()
    selectedItem.value = item
    const menuWidth = 192
    menuX.value = Math.max(e.clientX - menuWidth, 0)
    menuY.value = e.clientY
    menu.value = true
  }

  function closeMenu() {
    menu.value = false
  }

  function handleGlobalContextMenu(e) {
    if (!menu.value) return
    e.preventDefault()
    closeMenu()
  }

  watch(menu, function (isOpen) {
    if (isOpen) {
      window.addEventListener('contextmenu', handleGlobalContextMenu, true)
    } else {
      window.removeEventListener('contextmenu', handleGlobalContextMenu, true)
    }
  })

  onUnmounted(function () {
    window.removeEventListener('contextmenu', handleGlobalContextMenu, true)
  })

  const visibleActions = computed(function () {
    if (!selectedItem.value) return []
    return actions.filter(function (a) {
      return !a.visible || a.visible(selectedItem.value)
    })
  })

  const treeCtx = {
    refreshNode: refreshNode,
    purgeSubtree: purgeSubtree,
    findNodeById: findNodeById,
    reload: reload
  }

  async function runAction(action) {
    const target = selectedItem.value
    if (!target || !action) return
    menu.value = false
    setBusy(target.id, true)

    try {
      await action.handler(target, treeCtx)
    } catch (e) {
      reportError(e, { phase: 'action', action, node: target })
    } finally {
      setBusy(target.id, false)
      if (action.keepSelection !== true) {
        selectedItem.value = null
      }
    }
  }

  // ==================== Node Click ====================

  async function handleNodeClick(item) {
    if (item._dummy) return
    if (typeof onNodeClick !== 'function') return

    try {
      await onNodeClick(item, treeCtx)
    } catch (e) {
      reportError(e, { phase: 'nodeClick', node: item })
    }
  }

  // ======================= Wrapper =========================
  const changeReload = async (ctx, item, parentId) => {
    ctx.purgeSubtree(item)
    if (parentId) {
      const parent = ctx.findNodeById(parentId)
      if (parent) await ctx.refreshNode(parent)
    } else {
      await ctx.reload()
    }
  }

  const addReload = async (tree, item) => {
    await tree.refreshNode(item)
  }
  async function reset() {
    expandedKeys.value = []
    menu.value = false
    selectedItem.value = null
    busyIds.value = new Set()
    nodeIndex.clear()
    loadedCache.clear()
    treeData.value = await loadChildren()
  }

  return {
    expandedKeys: expandedKeys,
    treeData: treeData,
    menu: menu,
    menuX: menuX,
    menuY: menuY,
    selectedItem: selectedItem,
    visibleActions: visibleActions,
    isBusy: isBusy,

    init: init,
    reload: reload,
    reset: reset,

    changeReload: changeReload,
    addReload: addReload,

    refreshNode: refreshNode,
    purgeSubtree: purgeSubtree,
    findNodeById: findNodeById,

    openMenu: openMenu,
    closeMenu: closeMenu,
    runAction: runAction,
    handleNodeClick: handleNodeClick
  }
}
