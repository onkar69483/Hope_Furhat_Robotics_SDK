package furhatos.app.hopev1.audio

import java.io.File

/**
 * Simple test class for the AudioRecorder
 * You can run this separately to verify audio recording works correctly
 */
object AudioRecorderTest {
    @JvmStatic
    fun main(args: Array<String>) {
        println("Starting audio recorder test...")
        val recorder = AudioRecorder()

        // Start recording
        println("Recording will start in 2 seconds...")
        Thread.sleep(2000)

        println("Recording started - speak now!")
        val file = recorder.startRecording()

        // Record for 5 seconds
        Thread.sleep(5000)

        // Stop recording
        println("Stopping recording...")
        recorder.stopRecording()

        println("Recording saved to: ${file.absolutePath}")
        println("File exists: ${file.exists()}")
        println("File size: ${file.length()} bytes")

        // Open the file with the default system player
        if (file.exists() && file.length() > 0) {
            try {
                println("Attempting to play the recorded file...")
                val processBuilder = ProcessBuilder("cmd", "/c", "start", "wmplayer", file.absolutePath)
                processBuilder.start()
            } catch (e: Exception) {
                println("Couldn't open the file automatically: ${e.message}")
            }
        }
    }
}