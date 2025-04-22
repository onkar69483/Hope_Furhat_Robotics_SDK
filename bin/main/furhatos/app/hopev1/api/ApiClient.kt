package furhatos.app.hopev1.api

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

private const val API_URL = "http://localhost:5000/api/chat"

class ApiClient {
    fun sendMessage(message: String): String {
        try {
            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true

            // Create JSON request with the new format
            val jsonRequest = JSONObject().apply {
                put("user_input", message)
                put("voice_emotion", "neutral")
                put("text_sentiment", "neutral")
                put("session_id", "1")
            }

            // Send request
            val outputStreamWriter = OutputStreamWriter(connection.outputStream)
            outputStreamWriter.write(jsonRequest.toString())
            outputStreamWriter.flush()

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

                // Parse JSON response and return just the response string
                val jsonResponse = JSONObject(response.toString())
                return jsonResponse.getString("response")
            } else {
                return "Error: HTTP response code $responseCode"
            }
        } catch (e: Exception) {
            return "Error connecting to backend: ${e.message}"
        }
    }
}