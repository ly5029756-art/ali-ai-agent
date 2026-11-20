package com.ali.aiagent

data class AgentMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val isToolCall: Boolean = false
)

enum class MessageRole {
    USER,
    AGENT,
    SYSTEM
}

data class AgentStepResult(
    val done: Boolean,
    val thought: String,
    val toolName: String? = null,
    val toolArgument: String? = null,
    val finalReply: String? = null
)
