package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;

/**
 * Tree-walking expression evaluator, extended with the three chapter 7
 * challenges plus support for the comma and ternary operators added in
 * the chapter 6 parser:
 *
 *   1. Allow ordering comparisons (>, >=, <, <=) between two strings, in
 *      addition to two numbers. Mixed-type comparisons are still an error.
 *   2. Extend "+" so that if EITHER operand is a string, the other
 *      operand is converted to its string representation and the two
 *      are concatenated (e.g. "scone" + 4 -> "scone4").
 *   3. Division by zero is a runtime error rather than producing
 *      Infinity/NaN.
 */
class Interpreter implements Expr.Visitor<Object> {

  void interpret(Expr expression) {
    try {
      Object value = evaluate(expression);
      System.out.println(stringify(value));
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    }
  }

  @Override
  public Object visitLiteralExpr(Expr.Literal expr) {
    return expr.value;
  }

  @Override
  public Object visitGroupingExpr(Expr.Grouping expr) {
    return evaluate(expr.expression);
  }

  @Override
  public Object visitUnaryExpr(Expr.Unary expr) {
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      case BANG:
        return !isTruthy(right);
      case MINUS:
        checkNumberOperand(expr.operator, right);
        return -(double) right;
    }

    // Unreachable.
    return null;
  }

  // --- Challenge (ch. 6): ternary operator -----------------------------
  // Evaluate the condition, then evaluate ONLY the taken branch — this
  // is short-circuiting, same as C's ?:, so the untaken branch's side
  // effects never happen.
  @Override
  public Object visitTernaryExpr(Expr.Ternary expr) {
    Object condition = evaluate(expr.condition);

    if (isTruthy(condition)) {
      return evaluate(expr.thenBranch);
    } else {
      return evaluate(expr.elseBranch);
    }
  }

  @Override
  public Object visitBinaryExpr(Expr.Binary expr) {
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {

      // --- Challenge (ch. 6): comma operator --------------------------
      // Both sides were already evaluated above (for left-to-right side
      // effects, matching C). We simply discard the left value and
      // return the right one.
      case COMMA:
        return right;

      // --- Challenge 7.1: comparisons on strings, in addition to
      // numbers. We chose to allow ordering ONLY within the same type
      // (number/number or string/string), using natural numeric order
      // and Java's lexicographic String.compareTo() respectively.
      // Mixed-type comparisons (3 < "pancake") remain a runtime error,
      // the same way Java, Python 3, and JavaScript ('<' with strict
      // semantics) all refuse or special-case cross-type ordering
      // rather than silently coercing — coercion here would make the
      // ordering depend on obscure numeric-parsing rules and would
      // likely hide bugs rather than enable useful heterogeneous
      // collections.
      case GREATER:
        if (left instanceof Double && right instanceof Double) {
          return (double) left > (double) right;
        }
        if (left instanceof String && right instanceof String) {
          return ((String) left).compareTo((String) right) > 0;
        }
        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or two strings.");
      case GREATER_EQUAL:
        if (left instanceof Double && right instanceof Double) {
          return (double) left >= (double) right;
        }
        if (left instanceof String && right instanceof String) {
          return ((String) left).compareTo((String) right) >= 0;
        }
        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or two strings.");
      case LESS:
        if (left instanceof Double && right instanceof Double) {
          return (double) left < (double) right;
        }
        if (left instanceof String && right instanceof String) {
          return ((String) left).compareTo((String) right) < 0;
        }
        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or two strings.");
      case LESS_EQUAL:
        if (left instanceof Double && right instanceof Double) {
          return (double) left <= (double) right;
        }
        if (left instanceof String && right instanceof String) {
          return ((String) left).compareTo((String) right) <= 0;
        }
        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or two strings.");

      case MINUS:
        checkNumberOperands(expr.operator, left, right);
        return (double) left - (double) right;

      // --- Challenge 7.2: "+" stringifies the other operand whenever
      // either side is a string. Two numbers still add numerically.
      case PLUS:
        if (left instanceof Double && right instanceof Double) {
          return (double) left + (double) right;
        }
        if (left instanceof String || right instanceof String) {
          return stringify(left) + stringify(right);
        }
        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or at least one string.");

      // --- Challenge 7.3: division by zero is a runtime error. -------
      // Rationale: Java doubles quietly produce Infinity/-Infinity/NaN
      // for x/0.0, but silently propagating NaN through a program tends
      // to surface as a confusing wrong answer far from the real bug.
      // Languages like Python and Ruby raise an exception (ZeroDivisionError
      // / ZeroDivisionError) for exactly this reason, favoring a loud,
      // immediate failure over quiet float semantics — we follow that
      // model here rather than C's (which just produces IEEE-754 infinities).
      case SLASH:
        checkNumberOperands(expr.operator, left, right);
        if ((double) right == 0) {
          throw new RuntimeError(expr.operator, "Cannot divide by zero.");
        }
        return (double) left / (double) right;

      case STAR:
        checkNumberOperands(expr.operator, left, right);
        return (double) left * (double) right;

      case BANG_EQUAL:
        return !isEqual(left, right);
      case EQUAL_EQUAL:
        return isEqual(left, right);
    }

    // Unreachable.
    return null;
  }

  private Object evaluate(Expr expr) {
    return expr.accept(this);
  }

  private boolean isTruthy(Object object) {
    if (object == null) return false;
    if (object instanceof Boolean) return (boolean) object;
    return true;
  }

  private boolean isEqual(Object a, Object b) {
    if (a == null && b == null) return true;
    if (a == null) return false;

    return a.equals(b);
  }

  private void checkNumberOperand(Token operator, Object operand) {
    if (operand instanceof Double) return;
    throw new RuntimeError(operator, "Operand must be a number.");
  }

  private void checkNumberOperands(Token operator, Object left, Object right) {
    if (left instanceof Double && right instanceof Double) return;
    throw new RuntimeError(operator, "Operands must be numbers.");
  }

  private String stringify(Object object) {
    if (object == null) return "nil";

    if (object instanceof Double) {
      String text = object.toString();
      if (text.endsWith(".0")) {
        text = text.substring(0, text.length() - 2);
      }
      return text;
    }

    return object.toString();
  }
}
