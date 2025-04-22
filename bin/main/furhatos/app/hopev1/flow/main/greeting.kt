package furhatos.app.hopev1.flow.main

import furhatos.app.hopev1.api.ApiClient
import furhatos.app.hopev1.audio.AudioRecorder
import furhatos.app.hopev1.flow.Parent
import furhatos.flow.kotlin.*
import furhatos.nlu.common.No
import furhatos.nlu.common.Yes
import furhatos.gestures.Gestures
import kotlin.concurrent.thread
import furhatos.event.Event
import java.io.File

// Custom event for API response
class ApiResponseEvent(val response: String) : Event()

// Create a single instance of the API client and AudioRecorder
private val apiClient = ApiClient()
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
        audioRecorder.stopRecording()
        isListening = false

        val userMessage = it.text

        // Only process if there's actual text content
        if (userMessage.isNotBlank()) {
            // Show thinking state
            furhat.gesture(Gestures.Thoughtful)

            // Send user message to Python backend in a separate thread
            thread {
                val response = apiClient.sendMessage(userMessage)
                // Use an event to send the response back to the main thread
                send(ApiResponseEvent(response))
            }
        } else {
            // Empty response, just continue listening
            currentAudioFile = audioRecorder.startRecording()
            isListening = true
            furhat.listen()
        }
    }

    // Handle the API response event
    onEvent<ApiResponseEvent> {
        furhat.say(it.response)

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