package src.main.java.com.composer.core.domain.types;

import java.io.Serializable;

public class Interval implements Serializable {
    private static final long serialVersionUID = 1L;

    // --- REUSE BLUEPRINTS (Easily extended by the developer here) ---
    public static final Interval PERFECT_UNISON  = new Interval("PERFECT_UNISON", "1/1");

    // Pythagorean / Just Intonation Examples
    public static final Interval MINOR_THIRD_JUST= new Interval("MINOR_THIRD_JUST", "6/5");
    public static final Interval MAJOR_THIRD_JUST= new Interval("MAJOR_THIRD_JUST", "5/4");
    public static final Interval PERFECT_FOURTH  = new Interval("PERFECT_FOURTH", "4/3");
    public static final Interval PERFECT_FIFTH   = new Interval("PERFECT_FIFTH", "3/2");

    // Decimal and Integer Examples
    public static final Interval MICROTONAL_STEP = new Interval("MICROTONAL_STEP", "1.18");
    public static final Interval PERFECT_OCTAVE  = new Interval("PERFECT_OCTAVE", "2");

    // Array simulation to keep the UI selection dynamic and loopable
    private static final Interval[] VALUES = {
      PERFECT_UNISON, MINOR_THIRD_JUST, MAJOR_THIRD_JUST,
      PERFECT_FOURTH, PERFECT_FIFTH, MICROTONAL_STEP, PERFECT_OCTAVE
    };

    public static Interval[] values() { return VALUES; }
    // -----------------------------------------------------------------

    private final String name;
    private final String expression;
    private final double ratioValue;

    public Interval(String name, String expression) {
        this.name = name;
        this.expression = expression;
        this.ratioValue = parseExpression(expression);
    }

    public String name() { return name; }
    public String getExpression() { return expression; }
    public double getRatioValue() { return ratioValue; }

    /**
     * MATHEMATICAL PARSER: Resolves fractions (e.g. "3/2") and decimals (e.g. "1.18")
     */
    private double parseExpression(String expr) {
        if (expr.contains("/")) {
            String[] parts = expr.split("/");
            double numerator = Double.parseDouble(parts[0].trim());
            double denominator = Double.parseDouble(parts[1].trim());
            if (denominator == 0) throw new ArithmeticException("Division by zero in interval ratio.");
            return numerator / denominator;
        }
        return Double.parseDouble(expr.trim());
    }

    @Override
    public String toString() {
        return String.format("%s (%s -> val: %.4f)", name, expression, ratioValue);
    }
}
