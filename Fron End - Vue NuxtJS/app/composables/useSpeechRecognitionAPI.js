/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useSpeechRecognitionAPI = () => {
  let recognition
  let listening = false

  let exportContent = ''

  const ERROR_MESSAGES = {
    'no-speech': 'صدایی شنیده نشد. لطفاً دوباره تلاش کنید.',
    'audio-capture': 'میکروفون پیدا نشد یا در دسترس نیست.',
    'not-allowed': 'دسترسی به میکروفون رد شد.',
    'network': 'خطای شبکه در ارتباط با سرویس تشخیص گفتار.',
    'aborted': 'تشخیص گفتار لغو شد.',
    'language-not-supported': 'زبان انتخاب‌شده پشتیبانی نمی‌شود.',
    'service-not-allowed': 'سرویس تشخیص گفتار در دسترس نیست.'
  }

  const translateError = errorCode =>
    ERROR_MESSAGES[errorCode] || `خطای ناشناخته: ${errorCode}`

  const init = (onResult, onError, onEnd) => {
    if (
      !('SpeechRecognition' in window)
      && !('webkitSpeechRecognition' in window)
    ) {
      throw new Error('لطفاً از Chrome استفاده کنید.')
    }

    if (!recognition) {
      const SpeechRecognition
        = window.SpeechRecognition
          || window.webkitSpeechRecognition

      recognition = new SpeechRecognition()

      recognition.lang = 'fa-IR'
      recognition.continuous = true
      recognition.interimResults = true
    }

    recognition.onresult = (event) => {
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const time = new Date().toLocaleTimeString('fa-IR')
        const text = event.results[i][0].transcript

        if (event.results[i].isFinal) {
          exportContent += `[${time}] ${text}\r\n`

          onResult(time, text, true)
        } else {
          onResult(time, text, false)
        }
      }
    }

    recognition.onerror = (event) => {
      onError(translateError(event.error))
    }

    recognition.onstart = () => {
      listening = true
    }

    recognition.onend = () => {
      listening = false
      onEnd()
    }
  }

  const start = () => {
    try {
      recognition?.start()
    } catch (err) {
      console.warn('تشخیص گفتار از قبل در حال اجراست:', err)
    }
  }

  const stop = () => recognition?.stop()

  const isListening = () => listening

  const clear = () => {
    exportContent = ''
  }

  const exportFile = (fileName) => {
    const blob = new Blob([exportContent], {
      type: 'text/plain;charset=utf-8'
    })

    const link = document.createElement('a')

    link.href = URL.createObjectURL(blob)

    link.download
      = fileName
        || `فایل_${new Date().toLocaleDateString('fa-IR')}.txt`

    link.click()

    URL.revokeObjectURL(link.href)
  }

  return {
    init,
    start,
    stop,
    clear,
    exportFile,
    isListening
  }
}
