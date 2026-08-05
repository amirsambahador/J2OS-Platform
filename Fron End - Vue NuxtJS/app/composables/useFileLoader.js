/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
const scriptLoadPromises = new Map()
const styleLoadPromises = new Map()
const scriptRefCounts = new Map()
const styleRefCounts = new Map()

export const useFileLoader = () => {
  const importJavaScriptSourceFile = (src) => {
    scriptRefCounts.set(src, (scriptRefCounts.get(src) || 0) + 1)

    if (scriptLoadPromises.has(src)) {
      return scriptLoadPromises.get(src)
    }

    const promise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = src
      script.async = true
      script.setAttribute('data-dynamic', 'true')

      script.onload = () => resolve()
      script.onerror = () => {
        script.remove()
        scriptLoadPromises.delete(src)
        scriptRefCounts.delete(src)
        reject(new Error(`Failed to load JS: ${src}`))
      }

      document.head.appendChild(script)
    })

    scriptLoadPromises.set(src, promise)
    return promise
  }

  const importStyleSheetFile = (href) => {
    styleRefCounts.set(href, (styleRefCounts.get(href) || 0) + 1)

    if (styleLoadPromises.has(href)) {
      return styleLoadPromises.get(href)
    }

    const promise = new Promise((resolve, reject) => {
      const link = document.createElement('link')
      link.rel = 'stylesheet'
      link.href = href
      link.setAttribute('data-dynamic', 'true')

      link.onload = () => resolve()
      link.onerror = () => {
        link.remove()
        styleLoadPromises.delete(href)
        styleRefCounts.delete(href)
        reject(new Error(`Failed to load CSS: ${href}`))
      }

      document.head.appendChild(link)
    })

    styleLoadPromises.set(href, promise)
    return promise
  }

  const removeAllDynamicFiles = () => {
    document.querySelectorAll('[data-dynamic="true"]').forEach((el) => {
      const rawSource = el.getAttribute('src') || el.getAttribute('href')

      console.log('Removing dynamic file:', rawSource)

      scriptLoadPromises.delete(rawSource)
      styleLoadPromises.delete(rawSource)
      scriptRefCounts.delete(rawSource)
      styleRefCounts.delete(rawSource)

      el.remove()
    })
  }

  const removeStyleSheetFile = (href) => {
    const count = styleRefCounts.get(href) || 0

    if (count > 1) {
      styleRefCounts.set(href, count - 1)
      return
    }

    styleRefCounts.delete(href)
    const link = document.querySelector(`link[href="${href}"][data-dynamic="true"]`)
    if (link) {
      link.remove()
      styleLoadPromises.delete(href)
    }
  }

  const removeJavaScriptSourceFile = (src) => {
    const count = scriptRefCounts.get(src) || 0

    if (count > 1) {
      scriptRefCounts.set(src, count - 1)
      return
    }

    scriptRefCounts.delete(src)
    const script = document.querySelector(`script[src="${src}"][data-dynamic="true"]`)
    if (script) {
      script.remove()
      scriptLoadPromises.delete(src)
    }
  }

  return {
    importJavaScriptSourceFile,
    importStyleSheetFile,
    removeStyleSheetFile,
    removeJavaScriptSourceFile,
    removeAllDynamicFiles
  }
}
