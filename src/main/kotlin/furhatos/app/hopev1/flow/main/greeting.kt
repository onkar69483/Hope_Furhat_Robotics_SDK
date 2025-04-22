package furhatos.app.hopev1.flow.main

import furhatos.app.hopev1.api.ApiClient
import furhatos.app.hopev1.flow.Parent
import furhatos.flow.kotlin.*
import furhatos.nlu.common.No
import furhatos.nlu.common.Yes
import furhatos.gestures.Gestures
import kotlin.concurrent.thread
import furhatos.event.Event

// Custom event for API response
class ApiResponseEvent(val response: String) : Event()

// Create a single instance of the API client
private val apiClient = ApiClient()

val Greeting: State = state(Parent) {
    onEntry {
        // Instead of asking, just inform the user and start listening
        furhat.say("I'm ready. Just speak to me whenever you need something.")
        // Start listening without immediately expecting a response
        furhat.listen()
    }

    // This will trigger only when the user actually says something
    onResponse {
        val userMessage = it.text

        // Only process if there's actual text content
        if (userMessage.isNotBlank()) {
            // Show thinking state using a predefined gesture
            furhat.gesture(Gestures.Thoughtful)
//            furhat.say("Let me think about that...")

            // Send user message to Python backend in a separate thread
            thread {
                val response = apiClient.sendMessage(userMessage)
                // Use an event to send the response back to the main thread
                send(ApiResponseEvent(response))
            }
        } else {
            // If somehow we got an empty response, just continue listening
            furhat.listen()
        }
    }

    // Handle the API response event
    onEvent<ApiResponseEvent> {
        furhat.say(it.response)
        // Instead of asking a follow-up question, just start listening again
        furhat.listen()
    }

    // These specific responses can stay, but we'll also resume listening after them
    onResponse<Yes> {
        furhat.say("I'm listening, what would you like to know?")
        furhat.listen()
    }

    onResponse<No> {
        furhat.say("Alright, I'm still here if you need anything.")
        furhat.listen()
    }

    // Handle silence - just keep listening without saying anything
    onNoResponse {
        furhat.listen()
    }
}