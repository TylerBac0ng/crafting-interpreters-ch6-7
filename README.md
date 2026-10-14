# Crafting Interpreters — Chapters 6 & 7 Challenges

Solutions to the end-of-chapter challenges from [_Crafting Interpreters_](https://craftinginterpreters.com/) by Robert Nystrom, chapters **6 (Parsing Expressions)** and **7 (Evaluating Expressions)**, on top of the jlox tree-walk interpreter built earlier in the book.

## What's here

```
src/main/java/com/craftinginterpreters/lox/
  Parser.java        Chapter 6 challenges (comma operator, ternary operator,
                      error productions for missing left operand)
  Interpreter.java    Chapter 7 challenges (string comparisons, "+" on mixed
                      types, division-by-zero error) + evaluation support
                      for the comma/ternary operators added in Parser.java
docs/
  wiring-notes.md      The small additions needed elsewhere (TokenType,
                        Scanner, Expr.java) for these two files to compile
```

This repo only contains the two files that changed plus the AST/token
additions they depend on — it assumes you already have the rest of jlox
(`Token.java`, `TokenType.java`, `Scanner.java`, `Expr.java`, `Lox.java`,
`RuntimeError.java`) from the earlier chapters.

## Chapter 6 — Parsing Expressions

1. **Comma operator** — `a, b, c` evaluates left to right, discarding all
   but the last value. Given the same precedence/associativity as C
   (lowest precedence, left-associative).
2. **Ternary operator `?:`** — C-style conditional expression, added
   between comma and equality in the precedence table, right-associative.
3. **Error productions** — a binary operator appearing with no left-hand
   operand (e.g. `* 3`) is now reported as a parse error instead of
   crashing or being silently mis-parsed, and the parser recovers by
   parsing and discarding the right-hand operand.

## Chapter 7 — Evaluating Expressions

1. **String comparisons** — `<`, `<=`, `>`, `>=` now work on two strings
   (lexicographic order), in addition to two numbers. Mixed-type
   comparisons are still a runtime error.
2. **`+` on mixed types** — if either operand of `+` is a string, the
   other operand is converted to its string form and the two are
   concatenated (`"scone" + 4` → `"scone4"`).
3. **Division by zero** — raises a runtime error rather than producing
   `Infinity`/`NaN`.

The full write-up, including the two challenges
that ask for a design justification rather than just code, is submitted separately.

## Building

Drop `Parser.java` and `Interpreter.java` into your existing jlox source
tree (`com/craftinginterpreters/lox/`), apply the changes in
`docs/wiring-notes.md`, and build/run exactly as the book describes.
