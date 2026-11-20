package com.ali.aiagent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen() {
    var apiKey by remember { mutableStateOf("") }
    var goalInput by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    val messages = remember {
        mutableStateListOf(
            AgentMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.SYSTEM,
                text = "Ali AI Agent ready. Enter your Gemini API key and give a goal (e.g., 'Calculate 45 * 18 + sqrt(144) and tell me the current time')."
            )
        )
    }

    val coroutineScope = rememberCoroutineScope()

    fun startAgentLoop(userGoal: String) {
        if (userGoal.isBlank() || isRunning) return
        isRunning = true
        goalInput = ""

        messages.add(
            AgentMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.USER,
                text = userGoal.trim()
            )
        )

        coroutineScope.launch {
            val client = GeminiAgentClient(apiKey = apiKey.trim())
            val historyLog = mutableListOf<String>()
            val maxSteps = 5
            var finished = false

            try {
                for (step in 1..maxSteps) {
                    val stepResult = client.runNextStep(
                        goal = userGoal.trim(),
                        historyLog = historyLog,
                        stepIndex = step
                    )

                    if (stepResult.done) {
                        val answer = stepResult.finalReply ?: stepResult.thought.ifBlank { "Task completed." }
                        messages.add(
                            AgentMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.AGENT,
                                text = answer
                            )
                        )
                        finished = true
                        break
                    } else {
                        val toolName = stepResult.toolName
                        val toolArg = stepResult.toolArgument.orEmpty()
                        if (toolName.isNullOrBlank()) {
                            val fallback = stepResult.finalReply ?: stepResult.thought.ifBlank { "Completed." }
                            messages.add(
                                AgentMessage(
                                    id = UUID.randomUUID().toString(),
                                    role = MessageRole.AGENT,
                                    text = fallback
                                )
                            )
                            finished = true
                            break
                        }

                        messages.add(
                            AgentMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.SYSTEM,
                                text = "Step $step → Tool [$toolName] with arg: '$toolArg'",
                                isToolCall = true
                            )
                        )

                        val toolOutput = AgentTools.execute(toolName, toolArg)
                        historyLog.add("Step $step: tool=$toolName, arg=$toolArg -> $toolOutput")

                        messages.add(
                            AgentMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.SYSTEM,
                                text = "Observation: $toolOutput",
                                isToolCall = true
                            )
                        )
                    }
                }

                if (!finished) {
                    messages.add(
                        AgentMessage(
                            id = UUID.randomUUID().toString(),
                            role = MessageRole.AGENT,
                            text = "Reached maximum steps ($maxSteps). Partial progress:\n${historyLog.joinToString("\n")}"
                        )
                    )
                }
            } catch (e: Exception) {
                messages.add(
                    AgentMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.SYSTEM,
                        text = "Error: ${e.message ?: "Unexpected failure"}"
                    )
                )
            } finally {
                isRunning = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ali AI Agent", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(message = msg)
                }
            }

            if (isRunning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Agent thinking & executing...", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    placeholder = { Text("Enter a goal for the agent...") },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { startAgentLoop(goalInput) },
                    enabled = !isRunning && goalInput.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Run Agent"
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: AgentMessage) {
    val containerColor = when (message.role) {
        MessageRole.USER -> MaterialTheme.colorScheme.primaryContainer
        MessageRole.AGENT -> MaterialTheme.colorScheme.secondaryContainer
        MessageRole.SYSTEM -> if (message.isToolCall) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    }

    val label = when (message.role) {
        MessageRole.USER -> "You"
        MessageRole.AGENT -> "Ali Agent"
        MessageRole.SYSTEM -> if (message.isToolCall) "Tool Trace" else "System"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
