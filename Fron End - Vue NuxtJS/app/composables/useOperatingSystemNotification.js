/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useOperatingSystemNotification = () => {
  const showNotification = async (title, message, website) => {
    if (typeof Notification === 'undefined') {
      console.error('این مرورگر از نوتیفیکیشن سیستم‌عامل پشتیبانی نمی‌کند.')
      return false
    }

    const permission = await Notification.requestPermission()

    if (permission !== 'granted') {
      console.warn(`دسترسی به نوتیفیکیشن داده نشد (وضعیت: ${permission})`)
      return false
    }

    try {
      const notification = new Notification(title, {
        body: message
      })

      notification.onclick = () => {
        window.focus()
        if (website) {
          window.open(website, '_blank')
        }
        notification.close()
      }

      return true
    } catch (err) {
      console.error('نمایش نوتیفیکیشن ناموفق بود (احتمالاً محدودیت مرورگر موبایل):', err)
      return false
    }
  }

  return {
    showNotification
  }
}
