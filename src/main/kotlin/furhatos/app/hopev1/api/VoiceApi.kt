package furhatos.app.hopev1.api

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

private const val API_URL = "http://localhost:5000/api/voice_emotion"

class VoiceApi {
    /**
     * Sends the recorded audio file and user text to the voice emotion API endpoint
     *
     * @param audioFile The WAV file containing the recorded audio
     * @param text The transcribed text from user's speech
     * @return The response text from the API
     */
    fun sendVoiceData(audioFile: File, text: String): String {
        try {
            // Boundary for multipart form data
            val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()

            // Set up connection
            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.doOutput = true

            // Get output stream
            val outputStream = connection.outputStream
            val writer = outputStream.bufferedWriter()

            // Add the text field
            writer.appendLine("--$boundary")
            writer.appendLine("Content-Disposition: form-data; name=\"text\"")
            writer.appendLine()
            writer.appendLine(text)

            // Add the file
            writer.appendLine("--$boundary")
            writer.appendLine("Content-Disposition: form-data; name=\"file\"; filename=\"${audioFile.name}\"")
            writer.appendLine("Content-Type: audio/wav")
            writer.appendLine()
            writer.flush()

            // Write file data
            audioFile.inputStream().use { input ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
            }

            // End of multipart form data
            writer.appendLine()
            writer.appendLine("--$boundary--")
            writer.flush()
            writer.close()

            // Read response
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                val response = StringBuilder()
                var inputLine: String?

                while (bufferedReader.readLine().also { inputLine = it } != null) {
                    response.append(inputLine)
                }
                bufferedReader.close()

                // Parse JSON response and return just the response text
                val jsonResponse = JSONObject(response.toString())
                val responseText = jsonResponse.getString("response_text")

                return responseText
            } else {
                return "Error: HTTP response code $responseCode"
            }
        } catch (e: Exception) {
            return "Error connecting to voice emotion API: ${e.message}"
        }
    }
}