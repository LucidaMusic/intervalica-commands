package src.main.java.com.composer.core.domain.types;

import java.io.Serial;
import java.io.Serializable;

public class Interval implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    // --- Developer Blueprints ---
    public static final Interval CUSTOM             = new Interval("CUSTOM_EXPRESSION", "0"); // Intercept token
    public static final Interval PERFECT_UNISON     = new Interval("PERFECT_UNISON", "1/1");
    public static final Interval MINOR_THIRD_JUST   = new Interval("MINOR_THIRD_JUST", "6/5");
    public static final Interval MAJOR_THIRD_JUST   = new Interval("MAJOR_THIRD_JUST", "5/4");
    public static final Interval PERFECT_FOURTH     = new Interval("PERFECT_FOURTH", "4/3");
    public static final Interval PERFECT_FIFTH      = new Interval("PERFECT_FIFTH", "3/2");
    public static final Interval MICROTONAL_STEP    = new Interval("MICROTONAL_STEP", "1.18");
    public static final Interval PERFECT_OCTAVE     = new Interval("PERFECT_OCTAVE", "2");

    private static final Interval[] VALUES = {
      CUSTOM, PERFECT_UNISON, MINOR_THIRD_JUST, MAJOR_THIRD_JUST,
      PERFECT_FOURTH, PERFECT_FIFTH, MICROTONAL_STEP, PERFECT_OCTAVE
    };

    public static Interval[] values() { return VALUES; }

    private final String name;
    private final String expression;
    private final double ratioValue;

    public Interval(String name, String expression) {
        this.name = name;
        this.expression = expression;
        this.ratioValue = name.equals("CUSTOM_EXPRESSION") ? 0.0 : evaluateMathExpression(expression);
    }

    public String name() { return name; }
    public String getExpression() { return expression; }
    public double getRatioValue() { return ratioValue; }

    /**
     * ADVANCED INLINE MATH PARSER
     * Parses standard operators (+, -, *, /) and parenthesis recursively without external libraries.
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
                double x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected mathematical character: " + (char)ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if      (eat('+')) x += parseTerm(); // addition
                    else if (eat('-')) x -= parseTerm(); // subtraction
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if      (eat('*')) x *= parseFactor(); // multiplication
                    else if (eat('/')) {
                        double divisor = parseFactor();
                        if (divisor == 0) throw new ArithmeticException("Division by zero ratio.");
                        x /= divisor; // division
                    }
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return parseFactor(); // unary plus
                if (eat('-')) return -parseFactor(); // unary minus

                double x;
                int startPos = this.pos;
                if (eat('(')) { // parentheses
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Unexpected parsing factor character: " + (char)ch);
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
