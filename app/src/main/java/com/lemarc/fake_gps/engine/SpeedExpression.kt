package com.lemarc.fake_gps.engine

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/**
 * Expression mathématique de la vitesse v (en km/h) en fonction de :
 *  - t : temps écoulé depuis le début du déplacement, en secondes ;
 *  - d : distance parcourue, en mètres.
 *
 * Syntaxe : nombres, + - * / % ^, comparaisons < > <= >=, parenthèses,
 * constantes pi et e, fonctions sin cos tan asin acos atan atan2 sinh cosh tanh
 * sqrt abs exp ln log log2 log10 floor ceil round sign min max pow mod,
 * signaux square tri saw (période 2π, valeurs dans [-1 ; 1]), if(c; a; b)
 * et rand(a; b) (aléatoire uniforme).
 *
 * Séparateurs : les arguments d'une fonction se séparent par « ; » ou « , ». La décimale est
 * « . » partout ; « , » n'est acceptée comme décimale qu'en dehors des appels de fonction
 * (« 2,5 » vaut 2.5, mais « min(2,5) » a deux arguments). Même règle que dans l'aide de l'appli
 * (chaîne `speed_help`).
 *
 * Exemples : « 50 », « 30 + 20*sin(t/10) », « min(5*t; 130) », « if(t<60; 5; 15) ».
 */
class SpeedExpression private constructor(val source: String, private val root: Node) {

    fun evaluate(t: Double, d: Double = 0.0): Double = root.eval(Vars(t, d))

    /** Vitesse en km/h, négatifs / NaN ramenés à 0. Aucune borne supérieure. */
    fun speedKmh(t: Double, d: Double = 0.0): Double {
        val v = evaluate(t, d)
        return if (v.isNaN() || v < 0) 0.0 else v
    }

    /** true si l'expression est un simple nombre (vitesse constante). */
    val isConstant: Boolean get() = root is Num

    class ParseException(message: String) : Exception(message)

    private class Vars(val t: Double, val d: Double)

    private sealed interface Node {
        fun eval(v: Vars): Double
    }

    private class Num(val value: Double) : Node {
        override fun eval(v: Vars) = value
    }

    private class Var(val name: Char) : Node {
        override fun eval(v: Vars) = if (name == 't') v.t else v.d
    }

    private class Unary(val op: Char, val a: Node) : Node {
        override fun eval(v: Vars) = if (op == '-') -a.eval(v) else a.eval(v)
    }

    private class Binary(val op: Char, val a: Node, val b: Node) : Node {
        override fun eval(v: Vars): Double {
            val x = a.eval(v)
            val y = b.eval(v)
            return when (op) {
                '+' -> x + y
                '-' -> x - y
                '*' -> x * y
                '/' -> x / y
                '%' -> x.mod(y)
                '^' -> x.pow(y)
                '<' -> if (x < y) 1.0 else 0.0
                '>' -> if (x > y) 1.0 else 0.0
                'l' -> if (x <= y) 1.0 else 0.0
                'g' -> if (x >= y) 1.0 else 0.0
                else -> error("opérateur inconnu $op")
            }
        }
    }

    private class Call(val fn: Function, val args: List<Node>) : Node {
        override fun eval(v: Vars) = fn.impl(args.map { it.eval(v) })
    }

    private class Function(val arity: IntRange, val impl: (List<Double>) -> Double)

    private class Parser(val text: String) {
        var pos = 0

        /** Profondeur d'appel de fonction : la virgule n'est décimale qu'en dehors. */
        var callDepth = 0

        private fun isNumberChar(c: Char) = c.isDigit() || c == '.' || (c == ',' && callDepth == 0)

        fun parse(): Node {
            val node = comparison()
            skipSpaces()
            if (pos < text.length) fail("caractère inattendu « ${text[pos]} »")
            return node
        }

        private fun comparison(): Node {
            var left = additive()
            while (true) {
                skipSpaces()
                left = when {
                    match("<=") -> Binary('l', left, additive())
                    match(">=") -> Binary('g', left, additive())
                    match("<") -> Binary('<', left, additive())
                    match(">") -> Binary('>', left, additive())
                    else -> return left
                }
            }
        }

        private fun additive(): Node {
            var left = term()
            while (true) {
                skipSpaces()
                left = when {
                    match("+") -> Binary('+', left, term())
                    match("-") -> Binary('-', left, term())
                    else -> return left
                }
            }
        }

        private fun term(): Node {
            var left = unary()
            while (true) {
                skipSpaces()
                left = when {
                    match("*") -> Binary('*', left, unary())
                    match("/") -> Binary('/', left, unary())
                    match("%") -> Binary('%', left, unary())
                    else -> return left
                }
            }
        }

        private fun unary(): Node {
            skipSpaces()
            return when {
                match("-") -> Unary('-', unary())
                match("+") -> unary()
                else -> power()
            }
        }

        private fun power(): Node {
            val base = primary()
            skipSpaces()
            // Associatif à droite : 2^3^2 = 2^9 ; -x^2 = -(x^2).
            return if (match("^")) Binary('^', base, unary()) else base
        }

        private fun primary(): Node {
            skipSpaces()
            if (pos >= text.length) fail("expression incomplète")
            val c = text[pos]
            return when {
                c == '(' -> {
                    pos++
                    val inner = comparison()
                    expect(')')
                    inner
                }
                c.isDigit() || c == '.' -> number()
                c.isLetter() -> identifier()
                else -> fail("caractère inattendu « $c »")
            }
        }

        private fun number(): Node {
            val start = pos
            while (pos < text.length && isNumberChar(text[pos])) pos++
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                val save = pos
                pos++
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                if (pos < text.length && text[pos].isDigit()) {
                    while (pos < text.length && text[pos].isDigit()) pos++
                } else {
                    pos = save
                }
            }
            val raw = text.substring(start, pos).replace(',', '.')
            val value = raw.toDoubleOrNull() ?: fail("nombre invalide « $raw »")
            return Num(value)
        }

        private fun identifier(): Node {
            val start = pos
            while (pos < text.length && (text[pos].isLetterOrDigit() || text[pos] == '_')) pos++
            val name = text.substring(start, pos).lowercase()
            skipSpaces()
            if (pos < text.length && text[pos] == '(') {
                pos++
                val args = mutableListOf<Node>()
                skipSpaces()
                callDepth++
                if (!match(")")) {
                    do {
                        args += comparison()
                        skipSpaces()
                    } while (match(";") || match(","))
                    expect(')')
                }
                callDepth--
                if (name == "if") {
                    if (args.size != 3) fail("if attend 3 arguments")
                    return IfNode(args[0], args[1], args[2])
                }
                val fn = FUNCTIONS[name] ?: fail("fonction inconnue « $name »")
                if (args.size !in fn.arity) fail("nombre d'arguments incorrect pour $name")
                return Call(fn, args)
            }
            return when (name) {
                "t" -> Var('t')
                "d" -> Var('d')
                "pi" -> Num(Math.PI)
                "e" -> Num(Math.E)
                else -> fail("variable inconnue « $name » (utilisez t ou d)")
            }
        }

        private fun skipSpaces() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun match(token: String): Boolean {
            if (text.startsWith(token, pos)) {
                pos += token.length
                return true
            }
            return false
        }

        private fun expect(c: Char) {
            skipSpaces()
            if (pos >= text.length || text[pos] != c) fail("« $c » attendu")
            pos++
        }

        private fun fail(message: String): Nothing = throw ParseException(message)
    }

    private class IfNode(val cond: Node, val a: Node, val b: Node) : Node {
        override fun eval(v: Vars) = if (cond.eval(v) != 0.0) a.eval(v) else b.eval(v)
    }

    companion object {
        private fun f1(op: (Double) -> Double) = Function(1..1) { op(it[0]) }
        private fun f2(op: (Double, Double) -> Double) = Function(2..2) { op(it[0], it[1]) }

        private val FUNCTIONS: Map<String, Function> = mapOf(
            "sin" to f1(::sin), "cos" to f1(::cos), "tan" to f1(::tan),
            "asin" to f1(::asin), "acos" to f1(::acos), "atan" to f1(::atan),
            "sinh" to f1(::sinh), "cosh" to f1(::cosh), "tanh" to f1(::tanh),
            "sqrt" to f1(::sqrt), "abs" to f1 { abs(it) }, "exp" to f1(::exp),
            "ln" to f1(::ln), "log" to f1(::ln), "log2" to f1(::log2), "log10" to f1(::log10),
            "floor" to f1(::floor), "ceil" to f1(::ceil), "round" to f1(::round),
            "sign" to f1 { sign(it) },
            // Signaux périodiques de période 2π, à valeurs dans [-1 ; 1], alignés sur sin.
            "square" to f1 { if (sin(it) >= 0) 1.0 else -1.0 },
            "tri" to f1 { 2 / Math.PI * asin(sin(it)) },
            "saw" to f1 { x -> val u = x / (2 * Math.PI); 2 * (u - floor(u + 0.5)) },
            "atan2" to f2(::atan2), "pow" to f2 { a, b -> a.pow(b) }, "mod" to f2 { a, b -> a.mod(b) },
            "min" to Function(1..Int.MAX_VALUE) { it.min() },
            "max" to Function(1..Int.MAX_VALUE) { it.max() },
            "rand" to Function(0..2) { a ->
                when (a.size) {
                    0 -> Math.random()
                    1 -> Math.random() * a[0]
                    else -> a[0] + Math.random() * (a[1] - a[0])
                }
            },
        )

        /** Compile l'expression ; lève [ParseException] si elle est invalide. */
        fun parse(source: String): SpeedExpression {
            val text = source.trim()
            if (text.isEmpty()) throw ParseException("expression vide")
            return SpeedExpression(text, Parser(text).parse())
        }

        fun parseOrNull(source: String): SpeedExpression? =
            try {
                parse(source)
            } catch (_: ParseException) {
                null
            }
    }
}
