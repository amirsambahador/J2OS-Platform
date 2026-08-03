/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export function useCodeEditor() {
  const fileLoader = useFileLoader()
  const editor = ref()
  let tooltipStyleInjected = false

  async function init(editorId, options = {}) {
    await fileLoader.importJavaScriptSourceFile('/plugins/ace/ace.js')
    await fileLoader.importJavaScriptSourceFile('/plugins/ace/ext-language_tools.js')
    await fileLoader.importJavaScriptSourceFile('/plugins/ace/beautify.min.js')

    const language = options.language || 'java'
    const snippets = options.snippets || []
    const autocompletes = options.autocompletes || []
    const theme = options.theme || 'ace/theme/monokai'

    ace.config.set('basePath', '/plugins/ace')
    ace.require('ace/ext/language_tools')

    const snippetManager = ace.require('ace/snippets').snippetManager
    snippetManager.register(snippets, language)

    editor.value = ace.edit(editorId)
    editor.value.setTheme(theme)
    editor.value.session.setMode(`ace/mode/${language}`)

    editor.value.setOptions({
      fontSize: options.fontSize || 16,
      showPrintMargin: false,
      enableBasicAutocompletion: true,
      enableLiveAutocompletion: true,
      enableSnippets: false,
      useWorker: true
    })

    hideAceDocTooltip()

    editor.value.focus()

    const langTools = ace.require('ace/ext/language_tools')
    langTools.addCompleter({
      getCompletions: function (ed, session, pos, prefix, callback) {
        const currentMode = session.getMode().$id
        if (currentMode !== `ace/mode/${language}`) {
          callback(null, [])
          return
        }
        callback(null, autocompletes)
      }
    })

    return editor.value
  }
  /// /////////////////جلوگیری از نمایش Tooltip
  function hideAceDocTooltip() {
    if (tooltipStyleInjected) return
    tooltipStyleInjected = true

    const style = document.createElement('style')
    style.innerHTML = `
      .ace_tooltip.ace_doc-tooltip,
      .ace_tooltip {
        display: none !important;
      }
    `
    document.head.appendChild(style)
  }
  function get() {
    return editor.value.getValue()
  }

  function set(value) {
    editor.value.setValue(value, -1)
  }

  function downloadCode(filename = 'code.txt') {
    const blob = new Blob([get()], { type: 'text/plain;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = filename
    a.click()
    URL.revokeObjectURL(a.href)
  }

  function readOnly() {
    editor.value.setReadOnly(true)
    editor.value.renderer.$cursorLayer.element.style.display = 'none'
    editor.value.blur()
  }

  function readWrite() {
    editor.value.setReadOnly(false)
    editor.value.renderer.$cursorLayer.element.style.display = 'block'
    editor.value.focus()
  }

  function reformatCode() {
    if (!editor.value) return
    if (typeof js_beautify !== 'function') {
      throw new Error('کتابخانهٔ فرمت‌کننده لود نشده است')
    }

    const code = get()
    const cursorPos = editor.value.getCursorPosition()

    const formatted = js_beautify(code, {
      indent_size: 4,
      indent_char: ' ',
      brace_style: 'collapse',
      keep_array_indentation: false,
      space_before_conditional: true,
      preserve_newlines: true,
      max_preserve_newlines: 2
    })

    set(formatted)
    editor.value.moveCursorToPosition(cursorPos)
    editor.value.focus()
  }

  function destroy() {
    editor.value?.destroy?.()
    editor.value = null
    fileLoader.removeJavaScriptSourceFile('/plugins/ace/beautify.min.js')
    fileLoader.removeJavaScriptSourceFile('/plugins/ace/ext-language_tools.js')
    fileLoader.removeJavaScriptSourceFile('/plugins/ace/ace.js')
  }

  return {
    editor,
    init,
    get,
    set,
    downloadCode,
    readOnly,
    readWrite,
    reformatCode,
    destroy
  }
}
