package com.craftinginterpreters.lox;

import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

/**
 * Recursive-descent parser for Lox expressions, extended with the three
 * chapter 6 challenges:
 *
 *   1. Comma operator            ->  a, b, c
 *   2. C-style ternary operator  ->  a ? b : c
 *   3. Error productions for a binary operator missing its left operand,
 *      e.g. "+ 2" or "* 3"
 *
 * Updated grammar (highest -> lowest precedence, expression is the top):
 *
 *   expression -> comma ;
 *   comma      -> conditional ( "," conditional )* ;
 *   conditional-> equality ( "?" expression ":" conditional )? ;
 *   equality   -> comparison ( ( "!=" | "==" ) comparison )* ;
 *   comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 *   term       -> factor ( ( "-" | "+" ) factor )* ;
 *   factor     -> unary ( ( "/" | "*" ) unary )* ;
 *   unary      -> ( "!" | "-" ) unary | primary ;
 *   primary    -> NUMBER | STRING | "true" | "false" | "nil"
 *               | "(" expression ")" ;
 *
 * NOTE: this file assumes the TokenType enum has been extended with
 * QUESTION ('?') and COLON (':'), and that the Scanner has been taught to
 * emit them as single-character tokens (same way it handles COMMA, etc.).
 * It also assumes Expr.java has a new Ternary node (see Expr_Ternary.txt).
 */
class Parser {
  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  Expr parse() {
    try {
      return expression();
    } catch (ParseError error) {
      return null;
    }
  }

  private Expr expression() {
    return comma();
  }

  // --- Challenge 1: comma operator -----------------------------------
  // comma -> conditional ( "," conditional )* ;
  // Left-associative, same as C: evaluates left, discards it, evaluates
  // and returns right. We reuse Expr.Binary with a COMMA operator token
  // and give it special handling in the interpreter.
  private Expr comma() {
    Expr expr = conditional();

    while (match(COMMA)) {
      Token operator = previous();
      Expr right = conditional();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  // --- Challenge 2: ternary operator ----------------------------------
  // conditional -> equality ( "?" expression ":" conditional )? ;
  //
  // Sits between comma and equality, so "a, b ? c : d" parses as a comma
  // of (a, (b ? c : d)), matching C.
  //
  // The branch between "?" and ":" is a full `expression`, so it can
  // itself contain a comma (mirroring how C lets the middle operand of
  // ?: be a full expression even though the outer context might not
  // otherwise allow a comma).
  //
  // The final "else" branch recurses into `conditional` rather than
  // `expression`, which makes the operator right-associative:
  //   a ? b : c ? d : e   parses as   a ? b : (c ? d : e)
  private Expr conditional() {
    Expr expr = equality();

    if (match(QUESTION)) {
      Expr thenBranch = expression();
      consume(COLON, "Expect ':' after then branch of ternary expression.");
      Expr elseBranch = conditional();
      expr = new Expr.Ternary(expr, thenBranch, elseBranch);
    }

    return expr;
  }

  // --- Challenge 3: error productions for missing left operand --------
  // At each binary-operator precedence level, if we see the operator
  // *before* any operand, report it as an error, then parse (and
  // discard) a right-hand operand at the appropriate precedence so
  // parsing can continue, and keep trying to match more of that same
  // rule (in case there's a chain of bogus operators, or a valid
  // expression following).

  private Expr equality() {
    if (match(BANG_EQUAL, EQUAL_EQUAL)) {
      error(previous(), "Missing left-hand operand.");
      comparison();
      return equality();
    }

    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr comparison() {
    if (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      error(previous(), "Missing left-hand operand.");
      term();
      return comparison();
    }

    Expr expr = term();

    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr term() {
    // NOTE: unlike the other levels, "-" is also a valid *unary* prefix
    // operator, so a leading "-" must NOT be flagged here (it's handled
    // correctly by falling through to unary()). Only a leading "+" is
    // unambiguously an error, since Lox has no unary "+".
    if (match(PLUS)) {
      error(previous(), "Missing left-hand operand.");
      factor();
      return term();
    }

    Expr expr = factor();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr factor() {
    if (match(SLASH, STAR)) {
      error(previous(), "Missing left-hand operand.");
      unary();
      return factor();
    }

    Expr expr = unary();

    while (match(SLASH, STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr unary() {
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }

    return primary();
  }

  private Expr primary() {
    if (match(FALSE)) return new Expr.Literal(false);
    if (match(TRUE)) return new Expr.Literal(true);
    if (match(NIL)) return new Expr.Literal(null);

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    throw error(peek(), "Expect expression.");
  }

  // --- Helpers (unchanged from the book) -------------------------------

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();
    throw error(peek(), message);
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type == type;
  }

  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON) return;

      switch (peek().type) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
      }

      advance();
    }
  }
}
