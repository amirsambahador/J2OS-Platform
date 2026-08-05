/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useScreenRecorder = () => {
  let recorder
  let displayStream
  let micStream
  let combinedStream
  let audioContext
  let video
  let useMic = false
  let useSystemAudio = false
  let onRecordingEnded = null
  let pendingFileName = 'screen-record.webm'
  let starting = false

  const init = (videoId, captureMic = false, captureSystemAudio = false, onEnded = null) => {
    video = document.getElementById(videoId)

    if (!video) {
      throw new Error(`Video با id="${videoId}" پیدا نشد.`)
    }

    useMic = captureMic
    useSystemAudio = captureSystemAudio
    onRecordingEnded = onEnded
  }

  const finalizeRecording = (finishedChunks) => {
    const blob = new Blob(finishedChunks, { type: 'video/webm' })
    const url = URL.createObjectURL(blob)

    const link = document.createElement('a')
    link.href = url
    link.download = pendingFileName
    link.click()

    URL.revokeObjectURL(url)

    displayStream?.getTracks().forEach(track => track.stop())
    micStream?.getTracks().forEach(track => track.stop())
    combinedStream?.getTracks().forEach(track => track.stop())
    audioContext?.close()

    displayStream = null
    micStream = null
    audioContext = null
    combinedStream = null
    recorder = null

    onRecordingEnded?.()
  }

  const start = async () => {
    if (!video) {
      throw new Error('ویدیوی پیش‌نمایش تنظیم نشده است. ابتدا init را صدا بزنید.')
    }

    if (recorder || starting) {
      if (recorder && recorder.state === 'recording') {
        throw new Error('یک ضبط از قبل در حال اجراست')
      }
      throw new Error('ضبط قبلی هنوز در حال ذخیره‌سازی است، چند لحظه صبر کنید')
    }

    starting = true
    pendingFileName = 'screen-record.webm'

    try {
      try {
        displayStream = await navigator.mediaDevices.getDisplayMedia({
          video: true,
          audio: useSystemAudio
        })
      } catch (e) {
        throw new Error('دسترسی به صفحه لغو شد یا با خطا مواجه شد', { cause: e })
      }

      if (useMic) {
        try {
          micStream = await navigator.mediaDevices.getUserMedia({
            audio: true
          })
        } catch (e) {
          displayStream.getTracks().forEach(track => track.stop())
          displayStream = null
          throw new Error('دسترسی به میکروفون رد شد', { cause: e })
        }
      }

      try {
        const systemAudioTracks = useSystemAudio ? displayStream.getAudioTracks() : []
        const needsMixing = useMic && systemAudioTracks.length > 0

        let finalAudioTracks = []

        if (needsMixing) {
          audioContext = new AudioContext()
          const destination = audioContext.createMediaStreamDestination()

          audioContext.createMediaStreamSource(new MediaStream(systemAudioTracks)).connect(destination)
          audioContext.createMediaStreamSource(micStream).connect(destination)

          finalAudioTracks = destination.stream.getAudioTracks()
        } else if (useMic) {
          finalAudioTracks = micStream.getAudioTracks()
        } else if (systemAudioTracks.length > 0) {
          finalAudioTracks = systemAudioTracks
        }

        combinedStream = new MediaStream([
          ...displayStream.getVideoTracks(),
          ...finalAudioTracks
        ])

        video.srcObject = displayStream
        video.muted = true
        const recorderInstance = new MediaRecorder(combinedStream)
        const recordedChunks = []

        recorder = recorderInstance

        recorderInstance.ondataavailable = (event) => {
          if (event.data && event.data.size > 0) {
            recordedChunks.push(event.data)
          }
        }

        recorderInstance.onstop = () => {
          if (recorder === recorderInstance) {
            finalizeRecording(recordedChunks)
          }
        }

        const [displayVideoTrack] = displayStream.getVideoTracks()
        displayVideoTrack.addEventListener('ended', () => {
          if (recorder === recorderInstance && recorderInstance.state !== 'inactive') {
            recorderInstance.stop()
          }
        })

        recorderInstance.start()
      } catch (e) {
        displayStream?.getTracks().forEach(track => track.stop())
        micStream?.getTracks().forEach(track => track.stop())
        audioContext?.close()

        displayStream = null
        micStream = null
        audioContext = null
        combinedStream = null
        recorder = null

        throw e instanceof Error ? e : new Error('شروع ضبط با خطا مواجه شد', { cause: e })
      }
    } finally {
      starting = false
    }
  }

  const stop = (fileName = 'screen-record.webm') => {
    if (!recorder) {
      throw new Error('هیچ ضبطی برای توقف وجود ندارد')
    }

    if (recorder.state === 'inactive') {
      throw new Error('ضبط قبلاً متوقف شده است')
    }

    pendingFileName = fileName
    recorder.stop()
  }

  const isRecording = () => !!recorder && recorder.state === 'recording'

  return {
    init,
    start,
    stop,
    isRecording
  }
}
