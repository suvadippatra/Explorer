package com.troikoss.continuum_explorer.ui.editor

import java.util.regex.Pattern

/**
 * Pre-processes LaTeX Math equations ($ inline $ and $$ block $$) into clean,
 * formatted math notation for Markdown rendering.
 */
object LaTeXMathHelper {

    private val mathSymbols = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\theta" to "θ", "\\lambda" to "λ", "\\mu" to "μ",
        "\\pi" to "π", "\\sigma" to "σ", "\\tau" to "τ", "\\phi" to "φ",
        "\\omega" to "ω", "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ",
        "\\Lambda" to "Λ", "\\Sigma" to "Σ", "\\Omega" to "Ω", "\\infty" to "∞",
        "\\pm" to "±", "\\times" to "×", "\\div" to "÷", "\\neq" to "≠",
        "\\approx" to "≈", "\\le" to "≤", "\\ge" to "≥", "\\sum" to "∑",
        "\\int" to "∫", "\\sqrt" to "√", "\\partial" to "∂", "\\rightarrow" to "→"
    )

    fun formatLaTeXMath(markdown: String): String {
        if (!markdown.contains('$')) return markdown

        var result = markdown

        // Block Math $$ ... $$ -> formatted blockquote math
        try {
            val blockPattern = Pattern.compile("\\$\\$([\\s\\S]*?)\\$\\$")
            val blockMatcher = blockPattern.matcher(result)
            val blockBuffer = StringBuffer()
            while (blockMatcher.find()) {
                val rawMath = blockMatcher.group(1)?.trim() ?: ""
                val formatted = formatMathExpression(rawMath)
                blockMatcher.appendReplacement(blockBuffer, "\n> **Math:** `${formatted.replace("$", "\\$")}`\n")
            }
            blockMatcher.appendTail(blockBuffer)
            result = blockBuffer.toString()

            // Inline Math $ ... $ -> formatted inline code
            val inlinePattern = Pattern.compile("\\$([^\\$\\n]+?)\\$")
            val inlineMatcher = inlinePattern.matcher(result)
            val inlineBuffer = StringBuffer()
            while (inlineMatcher.find()) {
                val rawMath = inlineMatcher.group(1)?.trim() ?: ""
                val formatted = formatMathExpression(rawMath)
                inlineMatcher.appendReplacement(inlineBuffer, " `${formatted.replace("$", "\\$")}` ")
            }
            inlineMatcher.appendTail(inlineBuffer)
            result = inlineBuffer.toString()
        } catch (_: Exception) {
            // Fallback to original string if regex fails
        }

        return result
    }

    private fun formatMathExpression(math: String): String {
        var str = math
        // Superscripts
        str = str.replace("^2", "²").replace("^3", "³").replace("^n", "ⁿ").replace("^x", "ˣ")
        // Subscripts
        str = str.replace("_0", "₀").replace("_1", "₁").replace("_2", "₂").replace("_i", "ᵢ").replace("_n", "ₙ")
        // Frac \frac{a}{b} -> (a/b)
        val fracPattern = Pattern.compile("\\\\frac\\{([^\\}]+)\\}\\{([^\\}]+)\\}")
        val fracMatcher = fracPattern.matcher(str)
        val fracBuffer = StringBuffer()
        while (fracMatcher.find()) {
            val num = fracMatcher.group(1)
            val den = fracMatcher.group(2)
            fracMatcher.appendReplacement(fracBuffer, "($num / $den)")
        }
        fracMatcher.appendTail(fracBuffer)
        str = fracBuffer.toString()

        // Sqrt \sqrt{x} -> √(x)
        val sqrtPattern = Pattern.compile("\\\\sqrt\\{([^\\}]+)\\}")
        val sqrtMatcher = sqrtPattern.matcher(str)
        val sqrtBuffer = StringBuffer()
        while (sqrtMatcher.find()) {
            val inner = sqrtMatcher.group(1)
            sqrtMatcher.appendReplacement(sqrtBuffer, "√($inner)")
        }
        sqrtMatcher.appendTail(sqrtBuffer)
        str = sqrtBuffer.toString()

        // Replace Greek & Symbol Commands
        for ((cmd, sym) in mathSymbols) {
            str = str.replace(cmd, sym)
        }

        return str
    }
}
