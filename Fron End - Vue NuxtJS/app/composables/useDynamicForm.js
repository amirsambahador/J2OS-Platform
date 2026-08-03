/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useDynamicForm = (createEmptyRow) => {
  const rows = ref([])
  const isVisible = ref(false)
  let nextRowIndex = 1

  const createRow = rowIndex => ({
    id: `row-${rowIndex}`,
    ...createEmptyRow()
  })

  function resetToFirstRow() {
    nextRowIndex = 1
    rows.value = [createRow(nextRowIndex)]
    nextRowIndex += 1
  }

  const showForm = () => {
    resetToFirstRow()
    isVisible.value = true
  }

  const closeForm = () => {
    isVisible.value = false
    rows.value = []
  }

  const addRow = () => {
    rows.value.push(createRow(nextRowIndex))
    nextRowIndex += 1
  }

  const removeRow = (rowId) => {
    if (rows.value.length <= 1) return
    rows.value = rows.value.filter(r => r.id !== rowId)
  }

  const resetForm = () => {
    resetToFirstRow()
  }

  const getAllValues = () => {
    return rows.value.map(({ id, ...values }) => values)
  }

  return { rows, isVisible, showForm, closeForm, addRow, removeRow, resetForm, getAllValues }
}
