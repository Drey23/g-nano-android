package com.example.gnano.ui.theme

object Constants {

    val promptText = """
            Analyze this image for policy violations. 
            Check if the image contains nudity, underwear, or minors in suggestive contexts.
            The answer must describe what it is in the image.
            Return a JSON object with the following format:
            {
              "isViolating": true/false,
              "reason": "short explanation"
            }
        """.trimIndent()
}