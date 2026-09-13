package src.main.java.com.composer.core.domain.types;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Interval implements Serializable {
  @Serial
  private static final long serialVersionUID = 3L;

  private final String name;
  @Getter
  private final String expression;
  @Getter
  private final double ratioValue;
  @Getter
  private final String[] aliases;

  public Interval(String name, String expression, String... aliases) {
    this.name = name;
    this.expression = expression;
    this.ratioValue = name.equals("CUSTOM_EXPRESSION") ? 0.0 : evaluateMathExpression(expression);
    this.aliases = aliases;
  }

  // --- NEW: REUSE BLUEPRINTS WITH MULTIPLE RECALL ALIASES (Case Insensitive) ---
  public static final Interval PERFECT_UNISON = new Interval("PERFECT_UNISON", "1/1", "1", "1u", "unison");
  public static final Interval MINOR_SECOND = new Interval("MINOR_SECOND", "16/15", "2m", "minor2");
  public static final Interval MAJOR_SECOND = new Interval("MAJOR_SECOND", "9/8", "2M", "2", "major2");
  public static final Interval MINOR_THIRD_JUST = new Interval("MINOR_THIRD_JUST", "6/5", "3m", "minor3");
  public static final Interval MAJOR_THIRD_JUST = new Interval("MAJOR_THIRD_JUST", "5/4", "3M", "3", "major3");
  public static final Interval PERFECT_FOURTH = new Interval("PERFECT_FOURTH", "4/3", "4", "4j", "fourth");
  public static final Interval PERFECT_FIFTH = new Interval("PERFECT_FIFTH", "3/2", "5", "5j", "5pure", "fifth");
  public static final Interval PERFECT_OCTAVE = new Interval("PERFECT_OCTAVE", "2", "8", "8j", "octave");

  private static final Interval[] SYSTEM_INTERVALS = {
    PERFECT_UNISON, MINOR_SECOND, MAJOR_SECOND, MINOR_THIRD_JUST,
    MAJOR_THIRD_JUST, PERFECT_FOURTH, PERFECT_FIFTH, PERFECT_OCTAVE
  };

  private static final Map<String, Interval> ALIAS_MAP = new HashMap<>();

  static {
    for (Interval interval : SYSTEM_INTERVALS) {
      for (String alias : interval.aliases) {
        ALIAS_MAP.put(alias.toLowerCase(), interval);
      }
    }
  }

  public static Interval[] getSystemIntervals() {
    return SYSTEM_INTERVALS;
  }

  /**
   * Resolves an input token string into a verified Interval instance.
   */
  public static Interval fromAlias(String token) {
    if (token == null) return null;
    return ALIAS_MAP.get(token.trim().toLowerCase());
  }

  public String name() {
    return name;
  }

  /**
   * ADVANCED INLINE MATH PARSER (Unchanged, operates pure fractions/decimals calculations)
   */
  public static double evaluateMathExpression(String str) {
    return new Object() {
      int pos = -1, ch;

      void nextChar() {
        ch = (++pos < str.length()) ? str.charAt(pos) : -1;
      }

      boolean eat(int charToEat) {
        while (ch == ' ') nextChar();
        if (ch == charToEat) {
          nextChar();
          return true;
        }
        return false;
      }

      double parse() {
        nextChar();
        return parseExpression();
      }

      double parseExpression() {
        double x = parseTerm();
        for (; ; ) {
          if (eat('+')) x += parseTerm();
          else if (eat('-')) x -= parseTerm();
          else return x;
        }
      }

      double parseTerm() {
        double x = parseFactor();
        for (; ; ) {
          if (eat('*')) x *= parseFactor();
          else if (eat('/')) {
            double div = parseFactor();
            if (div == 0) throw new ArithmeticException();
            x /= div;
          } else return x;
        }
      }

      double parseFactor() {
        if (eat('+')) return parseFactor();
        if (eat('-')) return -parseFactor();
        double x;
        int startPos = this.pos;
        if (eat('(')) {
          x = parseExpression();
          eat(')');
        } else if ((ch >= '0' && ch <= '9') || ch == '.') {
          while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
          x = Double.parseDouble(str.substring(startPos, this.pos));
        } else {
          throw new RuntimeException("Unexpected token character: " + (char) ch);
        }
        return x;
      }
    }.parse();
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", name, expression);
  }
}
