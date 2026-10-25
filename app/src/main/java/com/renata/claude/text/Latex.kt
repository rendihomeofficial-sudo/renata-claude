package com.renata.claude.text

/**
 * Converts a small subset of LaTeX into readable Unicode text.
 *
 * The app renders math offline, so there is no KaTeX/MathJax runtime and no
 * network round trip. The goal is legibility (not typographic perfection):
 * fractions become `a/b`, roots become `√(x)`, symbols become their Unicode
 * counterpart and unknown commands fall back to their bare name.
 */
object Latex {

    private val symbols: Map<String, String> = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ",
        "upsilon" to "υ", "phi" to "φ", "varphi" to "φ", "chi" to "χ",
        "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ",
        "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        "sum" to "∑", "prod" to "∏", "int" to "∫", "oint" to "∮",
        "partial" to "∂", "nabla" to "∇", "infty" to "∞",
        "pm" to "±", "mp" to "∓", "times" to "×", "div" to "÷",
        "cdot" to "·", "circ" to "∘", "bullet" to "•",
        "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥",
        "neq" to "≠", "ne" to "≠", "approx" to "≈", "equiv" to "≡",
        "sim" to "∼", "simeq" to "≃", "cong" to "≅", "propto" to "∝",
        "in" to "∈", "notin" to "∉", "ni" to "∋", "subset" to "⊂",
        "subseteq" to "⊆", "supset" to "⊃", "supseteq" to "⊇",
        "cup" to "∪", "cap" to "∩", "setminus" to "∖",
        "forall" to "∀", "exists" to "∃", "nexists" to "∄",
        "neg" to "¬", "land" to "∧", "lor" to "∨", "wedge" to "∧",
        "vee" to "∨", "oplus" to "⊕", "otimes" to "⊗",
        "rightarrow" to "→", "to" to "→", "leftarrow" to "←",
        "leftrightarrow" to "↔", "Rightarrow" to "⇒", "Leftarrow" to "⇐",
        "Leftrightarrow" to "⇔", "implies" to "⇒", "iff" to "⇔",
        "angle" to "∠", "perp" to "⊥", "parallel" to "∥",
        "ldots" to "…", "dots" to "…", "cdots" to "⋯", "vdots" to "⋮",
        "ddots" to "⋱", "prime" to "′", "emptyset" to "∅",
        "varnothing" to "∅", "aleph" to "ℵ", "hbar" to "ℏ", "ell" to "ℓ",
        "Re" to "ℜ", "Im" to "ℑ", "degree" to "°", "flat" to "♭",
        "sharp" to "♯", "clubsuit" to "♣", "diamondsuit" to "♢",
        "heartsuit" to "♥", "spadesuit" to "♠", "star" to "⋆",
        "checkmark" to "✓", "qed" to "∎", "top" to "⊤", "bot" to "⊥",
    )

    private val blackboard = mapOf(
        "R" to "ℝ", "N" to "ℕ", "Z" to "ℤ", "Q" to "ℚ", "C" to "ℂ",
        "P" to "ℙ", "H" to "ℍ", "F" to "𝔽", "E" to "𝔼",
    )

    private val superscripts = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ',
        'd' to 'ᵈ', 'e' to 'ᵉ', 'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ',
        'j' to 'ʲ', 'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ', 'o' to 'ᵒ',
        'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ', 'u' to 'ᵘ',
        'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ',
    )

    private val subscripts = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ',
    )

    fun toUnicode(source: String): String {
        var text = source.trim()
        text = text.replace(Regex("\\\\begin\\{[a-zA-Z*]+\\}"), "")
            .replace(Regex("\\\\end\\{[a-zA-Z*]+\\}"), "")
        text = text.replace("\\\\", "\n")
        text = text.replace("\\left", "").replace("\\right", "")
        text = text.replace("\\!", "").replace("\\,", " ").replace("\\;", " ")
        text = text.replace("\\:", " ").replace("\\quad", "  ").replace("\\qquad", "    ")
        text = text.replace("\\%", "%").replace("\\$", "$").replace("\\&", "&")
        text = text.replace("\\#", "#").replace("\\_", "_").replace("\\{", "{").replace("\\}", "}")

        text = replaceFractions(text)
        text = replaceRoots(text)
        text = replaceWrapped(text, "text")
        text = replaceWrapped(text, "mathrm")
        text = replaceWrapped(text, "mathbf")
        text = replaceWrapped(text, "mathit")
        text = replaceWrapped(text, "operatorname")

        text = Regex("\\\\mathbb\\{([A-Za-z])}").replace(text) { m ->
            blackboard[m.groupValues[1]] ?: m.groupValues[1]
        }
        text = Regex("\\\\mathcal\\{([A-Za-z])}").replace(text) { m -> m.groupValues[1] }
        text = Regex("\\\\boldsymbol\\{([^{}]*)}").replace(text) { m -> m.groupValues[1] }

        text = Regex("\\\\([a-zA-Z]+)").replace(text) { m ->
            val name = m.groupValues[1]
            symbols[name] ?: name
        }

        text = applyScripts(text)
        return text.trim()
    }

    /** Replaces `\frac{a}{b}` with `(a)/(b)`, innermost first. */
    private fun replaceFractions(source: String): String {
        val marker = "\\frac"
        var text = source
        var guard = 0
        while (guard++ < 20) {
            val index = text.indexOf(marker)
            if (index < 0) break
            val openA = text.indexOf('{', index)
            if (openA < 0) break
            val closeA = matchBrace(text, openA)
            if (closeA < 0) break
            val openB = text.indexOf('{', closeA + 1)
            if (openB < 0) break
            val closeB = matchBrace(text, openB)
            if (closeB < 0) break
            val numerator = text.substring(openA + 1, closeA)
            val denominator = text.substring(openB + 1, closeB)
            text = text.substring(0, index) +
                "(${replaceFractions(numerator)})/(${replaceFractions(denominator)})" +
                text.substring(closeB + 1)
        }
        return text
    }

    /** Replaces `\sqrt{x}` with `√(x)`. */
    private fun replaceRoots(source: String): String {
        var text = source
        var guard = 0
        while (guard++ < 20) {
            val index = text.indexOf("\\sqrt")
            if (index < 0) break
            var cursor = index + "\\sqrt".length
            var degree = ""
            if (cursor < text.length && text[cursor] == '[') {
                val close = text.indexOf(']', cursor)
                if (close > cursor) {
                    degree = text.substring(cursor + 1, close)
                    cursor = close + 1
                }
            }
            val open = text.indexOf('{', cursor)
            if (open < 0) break
            val close = matchBrace(text, open)
            if (close < 0) break
            val body = replaceRoots(text.substring(open + 1, close))
            val prefix = if (degree.isBlank()) "√" else "${degree}√"
            text = text.substring(0, index) + "$prefix($body)" + text.substring(close + 1)
        }
        return text
    }

    /** Replaces `\command{content}` with the content itself. */
    private fun replaceWrapped(source: String, command: String): String {
        val marker = "\\$command"
        var text = source
        var searchFrom = 0
        var guard = 0
        while (guard++ < 40) {
            val index = text.indexOf(marker, searchFrom)
            if (index < 0) break
            val after = index + marker.length
            // `\textbf` must not be treated as `\text`
            if (after < text.length && text[after].isLetter()) {
                searchFrom = after
                continue
            }
            val open = text.indexOf('{', after)
            if (open < 0) break
            val close = matchBrace(text, open)
            if (close < 0) break
            val body = text.substring(open + 1, close)
            text = text.substring(0, index) + body + text.substring(close + 1)
            searchFrom = index + body.length
        }
        return text
    }

    /** Converts `^2` / `^{12}` and `_1` / `_{n}` into Unicode scripts. */
    private fun applyScripts(source: String): String {
        val builder = StringBuilder()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if ((c == '^' || c == '_') && i + 1 < source.length) {
                val (body, next) = readScriptBody(source, i + 1)
                val mapped = body.map { ch ->
                    if (c == '^') superscripts[ch] ?: ch else subscripts[ch] ?: ch
                }.joinToString("")
                builder.append(mapped)
                i = next
            } else {
                builder.append(c)
                i++
            }
        }
        return builder.toString()
    }

    /** Reads a script body, either a single character or a `{...}` group. */
    private fun readScriptBody(source: String, start: Int): Pair<String, Int> {
        if (start >= source.length) return "" to start
        if (source[start] == '{') {
            val close = matchBrace(source, start)
            if (close < 0) return "" to start
            return source.substring(start + 1, close) to close + 1
        }
        return source[start].toString() to start + 1
    }

    /** Returns the index of the `}` matching the `{` at [open]. */
    private fun matchBrace(source: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }
}
