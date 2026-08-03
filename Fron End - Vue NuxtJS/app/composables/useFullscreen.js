/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useFullscreen = () => {
  let element

  const init = (id) => {
    element = document.getElementById(id)

    if (!element) {
      throw new Error(`Element با id="${id}" پیدا نشد.`)
    }
  }

  const open = async () => {
    if (!element) return
    try {
      await element.requestFullscreen()
    } catch (err) {
      console.error('ورود به حالت تمام‌صفحه ناموفق بود:', err)
    }
  }

  const close = async () => {
    if (!document.fullscreenElement) return

    try {
      await document.exitFullscreen()
    } catch (err) {
      console.error('خروج از حالت تمام‌صفحه ناموفق بود:', err)
    }
  }

  const toggle = () => {
    if (document.fullscreenElement) {
      close()
    } else {
      open()
    }
  }

  return {
    init,
    open,
    close,
    toggle
  }
}
