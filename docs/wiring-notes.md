# Wiring notes: what else has to change

`Parser.java` and `Interpreter.java` assume three small additions elsewhere
in the book's existing files.

## 1. `TokenType.java` — two new token kinds

```java
enum TokenType {
  // ...existing tokens...
  QUESTION, COLON,
  // ...
}
```

## 2. `Scanner.java` — scan `?` and `:` as single-character tokens

In `scanToken()`, alongside the other single-character cases:

```java
case '?': addToken(QUESTION); break;
case ':': addToken(COLON); break;
```

## 3. `Expr.java` — add a `Ternary` node and a matching visitor method

If you're using the book's `GenerateAst` tool, add this to the list of
expression types it generates:

```java
"Ternary  : Expr condition, Expr thenBranch, Expr elseBranch"
```

If you're hand-writing `Expr.java` instead, add:

```java
abstract class Expr {
  interface Visitor<R> {
    R visitBinaryExpr(Binary expr);
    R visitGroupingExpr(Grouping expr);
    R visitLiteralExpr(Literal expr);
    R visitUnaryExpr(Unary expr);
    R visitTernaryExpr(Ternary expr); // <-- add this line
  }

  // ...existing Binary, Grouping, Literal, Unary classes...

  static class Ternary extends Expr {
    Ternary(Expr condition, Expr thenBranch, Expr elseBranch) {
      this.condition = condition;
      this.thenBranch = thenBranch;
      this.elseBranch = elseBranch;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitTernaryExpr(this);
    }

    final Expr condition;
    final Expr thenBranch;
    final Expr elseBranch;
  }

  abstract <R> R accept(Visitor<R> visitor);
}
```

Note: the comma operator does **not** need a new node — `Parser.java`
reuses the existing `Expr.Binary` with a `COMMA`-type operator token, and
`Interpreter.java` gives `COMMA` its own `case` inside `visitBinaryExpr()`.

If you also built an `AstPrinter` in chapter 5, it implements
`Expr.Visitor<String>`, so it needs a `visitTernaryExpr` method too or it
won't compile — a minimal one:

```java
@Override
public String visitTernaryExpr(Expr.Ternary expr) {
  return parenthesize("?:", expr.condition, expr.thenBranch, expr.elseBranch);
}
```
