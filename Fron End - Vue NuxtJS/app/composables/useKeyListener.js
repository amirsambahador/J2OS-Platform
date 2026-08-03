/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useKeyListener = () => {
  const setupKeyBoardShortcuts = (shortcuts) => {
    function normalizeKey(key) {
      return key.length === 1 ? key.toLowerCase() : key
    }

    function handleKeyEvent(event) {
      const key = normalizeKey(event.key)

      const isCtrl = event.ctrlKey
      const isShift = event.shiftKey
      const isAlt = event.altKey

      let keyCombo = ''
      if (isCtrl) keyCombo += 'Ctrl+'
      if (isShift) keyCombo += 'Shift+'
      if (isAlt) keyCombo += 'Alt+'
      keyCombo += key

      if (shortcuts[keyCombo]) {
        shortcuts[keyCombo](event)
      } else if (shortcuts[key] && !isCtrl && !isShift && !isAlt) {
        shortcuts[key](event)
      }
    }

    document.addEventListener('keydown', handleKeyEvent)

    return () => {
      document.removeEventListener('keydown', handleKeyEvent)
    }
  }

  return {
    setupKeyBoardShortcuts
  }
}
