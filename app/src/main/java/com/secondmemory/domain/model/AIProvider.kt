package com.secondmemory.domain.model

/**
 * Supported LLM providers.
 */
enum class AIProvider(val displayName: String, val defaultBaseUrl: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    GROQ("Groq", "https://api.groq.com/openai/v1"),
    CUSTOM("Custom / Ollama", "http://localhost:11434/v1")
}
