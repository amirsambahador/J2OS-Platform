/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export function useRichText() {
  const fileLoader = useFileLoader()
  const editor = ref()

  async function init(editorId, options = {}) {
    await fileLoader.importStyleSheetFile('/plugins/suneditor/suneditor.min.css')
    await fileLoader.importJavaScriptSourceFile('/plugins/suneditor/suneditor.min.js')
    editor.value = SUNEDITOR.create(editorId, {
      width: options.width || '100%',
      height: options.height || '400PX',
      /*
      فونت متن ورودی کاربر
       */
      defaultStyle: 'font-family: Vazirmatn; font-size:14px;',
      placeholder: options.placeholder || '',

      rtl: true,

      buttonList: options.buttonList || [
        ['formatBlock'],

        ['bold', 'underline', 'italic', 'strike'],

        ['fontColor', 'hiliteColor'],

        ['removeFormat'],

        ['align'],

        ['list'],

        ['blockquote'],

        ['table'],

        ['codeView'],

        ['fullScreen']
      ]
    })

    injectEditorFont()

    return editor.value
  }

  /// //////////////فونت منو ها/////////////////////
  function injectEditorFont() {
    if (document.getElementById('suneditor-font-override')) return
    const style = document.createElement('style')
    style.id = 'suneditor-font-override'
    style.textContent = `
    .sun-editor, .sun-editor * {
      font-family: 'Vazirmatn', sans-serif !important;
    }
  `
    document.head.appendChild(style)
  }

  //////////////////////////////////////////////

  function getHtml() {
    return editor.value.getContents()
  }

  function getText() {
    return editor.value.getText()
  }

  function setHtml(html) {
    editor.value.setContents(html)
  }

  function clear() {
    editor.value.setContents('')
  }

  function downloadFile(content, filename, mimeType) {
    const blob = new Blob([content], { type: mimeType })
    const url = URL.createObjectURL(blob)

    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()

    URL.revokeObjectURL(url)
  }

  function exportHTML(filename = 'document.html') {
    downloadFile(getHtml(), filename, 'text/html')
  }

  function exportText(filename = 'document.txt') {
    downloadFile(getText(), filename, 'text/plain')
  }

  function setReadOnly(value) {
    editor.value.readOnly(value)
    const wysiwygFrame = editor.value.core.context.element.wysiwyg
    wysiwygFrame.style.caretColor = value ? 'transparent' : ''
    if (value) {
      wysiwygFrame.blur()
    }
  }

  function destroy() {
    editor.value?.destroy?.()
    editor.value = null
    fileLoader.removeJavaScriptSourceFile('/plugins/suneditor/suneditor.min.js')
    fileLoader.removeStyleSheetFile('/plugins/suneditor/suneditor.min.css')
  }

  return {
    editor,
    init,
    getHtml,
    getText,
    setHtml,
    clear,
    exportHTML,
    exportText,
    setReadOnly,
    destroy
  }
}
