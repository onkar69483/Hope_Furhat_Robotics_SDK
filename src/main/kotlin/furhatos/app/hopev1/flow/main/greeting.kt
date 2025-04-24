package furhatos.app.hopev1.flow.main

import furhatos.app.hopev1.api.VoiceApi
import furhatos.app.hopev1.audio.AudioRecorder
import furhatos.app.hopev1.flow.Parent
import furhatos.flow.kotlin.*
import furhatos.nlu.common.No
import furhatos.nlu.common.Yes
import furhatos.gestures.Gestures
import kotlin.concurrent.thread
import furhatos.event.Event
import java.io.File
import org.json.JSONObject

// Custom event for Voice API response with all emotion data
class VoiceApiResponseEvent(
    val responseText: String,
    val faceEmotion: String,
    val voiceEmotion: String,
    val textSentiment: JSONObject
) : Event()

// Create a single instance of the Voice API and AudioRecorder
private val voiceApi = VoiceApi()
private val audioRecorder = AudioRecorder()
private var currentAudioFile: File? = null
private var isListening = false

val Greeting: State = state(Parent) {
    onEntry {
        // Inform the user and start listening
        furhat.say("I'm ready. Just speak to me whenever you need something.")

        // Start a new recording session
        currentAudioFile = audioRecorder.startRecording()
        isListening = true
        furhat.listen()
    }

    // This will trigger when the user says something
    onResponse {
        // Stop recording as soon as we get a response
        val audioFile = currentAudioFile
        audioRecorder.stopRecording()

        // Get the timing information
        val startTime = audioRecorder.getStartTime()
        val endTime = audioRecorder.getEndTime()

        isListening = false

        val userMessage = it.text

        // Only process if there's actual text content and we have an audio file
        if (userMessage.isNotBlank() && audioFile != null && audioFile.exists()) {
            // Show thinking state
            furhat.gesture(Gestures.Thoughtful)

            // Send user voice data to Python backend in a separate thread
            thread {
                val apiResponse = voiceApi.sendVoiceData(audioFile, userMessage, startTime, endTime)
                try {
                    val jsonResponse = JSONObject(apiResponse)

                    // Extract all the data we need
                    val responseText = jsonResponse.getString("response_text")
                    val faceEmotion = jsonResponse.getString("face_emotion")
                    val voiceEmotion = jsonResponse.getString("voice_emotion")
                    val textSentiment = jsonResponse.getJSONObject("text_sentiment")

                    // Send all the data back to the main thread
                    send(VoiceApiResponseEvent(responseText, faceEmotion, voiceEmotion, textSentiment))
                } catch (e: Exception) {
                    // Fallback in case of parsing error
                    println("Error parsing API response: ${e.message}")
                    send(VoiceApiResponseEvent(
                        "I'm sorry, there was an error processing your request.",
                        "unknown",
                        "unknown",
                        JSONObject("{\"sentiment\":\"unknown\",\"emotion\":\"unknown\"}")
                    ))
                }
            }
        } else {
            // Error handling if no audio file is available
            if (audioFile == null || !audioFile.exists()) {
                furhat.say("I'm sorry, there was an issue with the audio recording.")
            }

            // Start a new recording session
            currentAudioFile = audioRecorder.startRecording()
            isListening = true
            furhat.listen()
        }
    }

    // Handle the Voice API response event
    onEvent<VoiceApiResponseEvent> {
        // Make the robot speak only the response text
        furhat.say(it.responseText)

        // Print all the emotion data to the web interface
        println("=== EMOTION ANALYSIS ===")
        println("Face Emotion: ${it.faceEmotion}")
        println("Voice Emotion: ${it.voiceEmotion}")
        println("Text Sentiment: ${it.textSentiment.getString("sentiment")}")
        println("Text Emotion: ${it.textSentiment.getString("emotion")}")
        println("=======================")

        // Start a new recording session after the response
        currentAudioFile = audioRecorder.startRecording()
        isListening = true
        furhat.listen()
    }

    onResponse<Yes> {
        // We already stopped recording in the main onResponse handler
        furhat.say("I'm listening, what would you like to know?")

        // Start a new recording session
        currentAudioFile = audioRecorder.startRecording()
        isListening = true
        furhat.listen()
    }

    onResponse<No> {
        // We already stopped recording in the main onResponse handler
        furhat.say("Alright, I'm still here if you need anything.")

        // Start a new recording session
        currentAudioFile = audioRecorder.startRecording()
        isListening = true
        furhat.listen()
    }

    // Handle silence - if no response is detected
    onNoResponse {
        // Only restart if there was no speech detected
        audioRecorder.stopRecording()
        isListening = false

        // Start a new recording session
        currentAudioFile = audioRecorder.startRecording()
        isListening = true
        furhat.listen()
    }

    // Clean up when leaving this state
    onExit {
        // Ensure recording is stopped when leaving this state
        if (isListening) {
            audioRecorder.stopRecording()
            isListening = false
        }
    }
}