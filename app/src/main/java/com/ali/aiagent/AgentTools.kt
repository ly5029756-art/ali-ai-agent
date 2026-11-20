package com.ali.aiagent

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

object AgentTools {

    fun availableToolsDescription(): String {
        return """
            Available tools:
            1. calc: Evaluate basic math expressions (+, -, *, /, ^, sqrt(x)). Example: tool="calc", arg="25 * 4 + 10"
            2. time: Get current date and time. Example: tool="time", arg=""
            3. notes: Format or summarize a quick note/checklist. Example: tool="notes", arg="Buy milk, study Kotlin"
        """.trimIndent()
    }

    fun execute(toolName: String, argument: String): String {
        return when (toolName.trim().lowercase(Locale.ROOT)) {
            "calc" -> evaluateMath(argument)
            "time" -> currentDateTime()
            "notes" -> formatNote(argument)
            else -> "Unknown tool '$toolName'. ${availableToolsDescription()}"
        }
    }

    private fun currentDateTime(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return "Current time: ${formatter.format(Date())}"
    }

    private fun formatNote(raw: String): String {
        val items = raw.split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (items.isEmpty()) return "No note content provided."
        return items.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n")
    }

    private fun evaluateMath(expr: String): String {
        return try {
            val cleaned = expr.replace(" ", "")
            if (cleaned.startsWith("sqrt(") && cleaned.endsWith(")")) {
                val inside = cleaned.removePrefix("sqrt(").removeSuffix(")").toDouble()
                return "Result: ${sqrt(inside)}"
            }
            val result = evalSimpleExpression(cleaned)
            val formatted = if (result % 1.0 == 0.0) result.toLong().toString() else result.toString()
            "Result: $formatted"
        } catch (e: Exception) {
            "Math error: ${e.message ?: "invalid expression"}"
        }
    }

    private fun evalSimpleExpression(expr: String): Double {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()

        for (i in expr.indices) {
            val ch = expr[i]
            val isUnaryMinus = ch == '-' && (i == 0 || expr[i - 1] in listOf('+', '-', '*', '/', '^'))
            if (ch.isDigit() || ch == '.' || isUnaryMinus) {
                current.append(ch)
            } else if (ch in listOf('+', '-', '*', '/', '^')) {
                if (current.isEmpty()) throw IllegalArgumentException("Unexpected operator '$ch'")
                tokens.add(current.toString())
                current.clear()
                tokens.add(ch.toString())
            } else {
                throw IllegalArgumentException("Unsupported character '$ch'")
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        if (tokens.isEmpty()) throw IllegalArgumentException("Empty expression")

        val values = mutableListOf<Double>()
        val ops = mutableListOf<String>()

        fun precedence(op: String): Int = when (op) {
            "^" -> 3
            "*", "/" -> 2
            "+", "-" -> 1
            else -> 0
        }

        fun applyTopOperator() {
            val op = ops.removeAt(ops.lastIndex)
            val b = values.removeAt(values.lastIndex)
            val a = values.removeAt(values.lastIndex)
            val res = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> {
                    if (b == 0.0) throw ArithmeticException("Division by zero")
                    a / b
                }
                "^" -> a.pow(b)
                else -> throw IllegalArgumentException("Unknown operator $op")
            }
            values.add(res)
        }

        for (token in tokens) {
            val num = token.toDoubleOrNull()
            if (num != null) {
                values.add(num)
            } else {
                while (ops.isNotEmpty() && precedence(ops.last()) >= precedence(token)) {
                    applyTopOperator()
                }
                ops.add(token)
            }
        }
        while (ops.isNotEmpty()) {
            applyTopOperator()
        }
        return values.first()
    }
}
