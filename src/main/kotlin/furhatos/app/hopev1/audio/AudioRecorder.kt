package furhatos.app.hopev1.audio

import java.io.File
import javax.sound.sampled.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

/**
 * Handles recording audio from the microphone with Voice Activity Detection (VAD)
 * Only starts recording when speech is detected and stops after silence
 */
class AudioRecorder {
    private var recorder: Thread? = null
    private var vadMonitor: Thread? = null
    private var isRecording = false
    private var targetDataLine: TargetDataLine? = null
    private val audioFormat = AudioFormat(16000f, 16, 1, true, false)
    private val audioFolder = File("src/main/kotlin/furhatos/app/hopev1/audio").apply {
        if (!exists()) mkdirs()
    }

    // VAD parameters
    private val silenceThreshold = 500 // Threshold amplitude to detect speech
    private val silenceDurationToStop = 1000 // Stop after 1 second of silence (in ms)
    private var lastAudioTime = 0L
    private var recordingStarted = false

    // Timing variables
    private var startTime = 0.0
    private var endTime = 0.0

    /**
     * Starts recording audio with voice activity detection
     * @return The File where the audio will be saved
     */
    fun startRecording(): File {
        synchronized(this) {
            if (isRecording) {
                stopRecording()
            }

            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val outputFile = File(audioFolder, "speech_$timestamp.wav").apply {
                parentFile.mkdirs()
            }

            isRecording = true
            recordingStarted = false
            lastAudioTime = System.currentTimeMillis()

            // Reset timing variables
            startTime = 0.0
            endTime = 0.0

            // Create a temporary audio buffer
            val audioBuffer = ByteArrayOutputStream()

            recorder = thread(start = true) {
                try {
                    // Set up audio input
                    val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
                    targetDataLine = AudioSystem.getLine(info) as TargetDataLine
                    targetDataLine?.apply {
                        open(audioFormat)
                        start()
                    }

                    // Create audio stream
                    val bufferSize = 4096
                    val buffer = ByteArray(bufferSize)

                    println("Starting audio monitoring for ${outputFile.absolutePath}")

                    while (isRecording) {
                        val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: 0
                        if (bytesRead > 0) {
                            // Process audio for VAD
                            processAudioChunk(buffer, bytesRead, audioBuffer)
                        }

                        // Give other threads a chance to run
                        Thread.sleep(10)
                    }

                    // Write the collected audio to file if speech was detected
                    if (recordingStarted && audioBuffer.size() > 0) {
                        writeWavFile(audioBuffer, outputFile)
                        println("Finished writing audio file: ${outputFile.absolutePath}")
                    } else if (outputFile.exists()) {
                        outputFile.delete()
                        println("No speech detected, deleted empty file")
                    }

                } catch (e: Exception) {
                    println("Error recording audio: ${e.message}")
                    e.printStackTrace()
                } finally {
                    synchronized(this@AudioRecorder) {
                        targetDataLine?.let {
                            if (it.isOpen) {
                                it.stop()
                                it.close()
                            }
                        }
                        targetDataLine = null
                        isRecording = false
                    }
                }
            }

            return outputFile
        }
    }

    /**
     * Process an audio chunk for voice activity detection
     */
    private fun processAudioChunk(buffer: ByteArray, bytesRead: Int, audioBuffer: ByteArrayOutputStream) {
        // Calculate audio energy
        var energy = 0.0
        for (i in 0 until bytesRead step 2) {
            if (i + 1 < bytesRead) {
                // Convert bytes to short samples
                val sample = (buffer[i+1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
                energy += Math.abs(sample.toDouble())
            }
        }
        energy /= (bytesRead / 2)

        // Speech detection logic
        if (energy > silenceThreshold) {
            if (!recordingStarted) {
                println("Speech detected, starting to record")
                recordingStarted = true
                // Record the start time when speech is first detected
                startTime = System.currentTimeMillis() / 1000.0
            }

            // Store audio data
            audioBuffer.write(buffer, 0, bytesRead)
            lastAudioTime = System.currentTimeMillis()

        } else if (recordingStarted) {
            // Still write during short silences
            audioBuffer.write(buffer, 0, bytesRead)

            // Stop if silence is too long
            val silenceDuration = System.currentTimeMillis() - lastAudioTime
            if (silenceDuration > silenceDurationToStop) {
                println("Silence detected for $silenceDuration ms, stopping recording")
                // Record the end time when stopping due to silence
                endTime = System.currentTimeMillis() / 1000.0
                isRecording = false
            }
        }
    }

    /**
     * Write collected audio data to a WAV file
     */
    private fun writeWavFile(audioData: ByteArrayOutputStream, outputFile: File) {
        val byteArray = audioData.toByteArray()

        val byteArrayInputStream = javax.sound.sampled.AudioInputStream(
            byteArray.inputStream(),
            audioFormat,
            byteArray.size.toLong() / audioFormat.frameSize
        )

        AudioSystem.write(
            byteArrayInputStream,
            AudioFileFormat.Type.WAVE,
            outputFile
        )
    }

    /**
     * Stops the current recording
     */
    fun stopRecording() {
        synchronized(this) {
            if (!isRecording) return

            println("Stopping audio recording...")
            // If end time wasn't already set by silence detection
            if (endTime == 0.0 && recordingStarted) {
                endTime = System.currentTimeMillis() / 1000.0
            }

            isRecording = false

            targetDataLine?.apply {
                try {
                    stop()
                    close()
                } catch (e: Exception) {
                    println("Error closing audio line: ${e.message}")
                }
            }
            targetDataLine = null

            // Wait for the recorder thread to finish
            recorder?.join(500)
            recorder?.interrupt()
            recorder = null
        }
    }

    /**
     * Checks if speech was detected in the current recording
     */
    fun wasSpeechDetected(): Boolean {
        return recordingStarted
    }

    /**
     * Gets the time of the last detected audio
     */
    fun getLastAudioTime(): Long {
        return lastAudioTime
    }

    /**
     * Gets the start time when speech was first detected
     */
    fun getStartTime(): Double {
        return startTime
    }

    /**
     * Gets the end time when recording was stopped
     */
    fun getEndTime(): Double {
        return endTime
    }
}