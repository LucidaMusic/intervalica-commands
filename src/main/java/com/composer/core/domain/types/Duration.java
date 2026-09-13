package src.main.java.com.composer.core.domain.types;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Duration implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final double beatsValue; // Core metric: exact duration expressed in mathematical beats
    private final String visualIcon; // Safe Unicode visual reference
    private final String[] aliases;

    public Duration(String name, double beatsValue, String visualIcon, String... aliases) {
        this.name = name;
        this.beatsValue = beatsValue;
        this.visualIcon = visualIcon;
        this.aliases = aliases;
    }

    // --- REUSE BLUEPRINTS WITH MULTIPLE RECALL ALIASES ---
    // Grounded on Quarter Note = 1.0 Beat reference scale
    public static final Duration WHOLE      = new Duration("WHOLE", 4.0, "[ 𝅝 ] Whole Note", "4", "4.0", "whole", "semibreve");
    public static final Duration HALF       = new Duration("HALF", 2.0, "[ 𝅗𝅥 ] Half Note", "2", "2.0", "half", "minim");
    public static final Duration QUARTER    = new Duration("QUARTER", 1.0, "[ 𝅘𝅥 ] Quarter Note", "1", "1.0", "quarter", "crotchet");
    public static final Duration EIGHTH     = new Duration("EIGHTH", 0.5, "[ 𝅘𝅥𝅮 ] Eighth Note", "0.5", "eighth", "8th", "quaver");
    public static final Duration SIXTEENTH  = new Duration("SIXTEENTH", 0.25, "[ 𝅘𝅥𝅯 ] Sixteenth Note", "0.25", "sixteenth", "16th", "semiquaver");

    private static final Duration[] SYSTEM_DURATIONS = {
      WHOLE, HALF, QUARTER, EIGHTH, SIXTEENTH
    };

    private static final Map<String, Duration> ALIAS_MAP = new HashMap<>();

    static {
        for (Duration duration : SYSTEM_DURATIONS) {
            for (String alias : duration.aliases) {
                ALIAS_MAP.put(alias.toLowerCase(), duration);
            }
        }
    }

    public static Duration[] getSystemDurations() { return SYSTEM_DURATIONS; }

    /**
     * Resolves an input token (text name, integer or fractional decimal) into a verified Duration instance.
     */
    public static Duration fromAlias(String token) {
        if (token == null) return null;
        return ALIAS_MAP.get(token.trim().toLowerCase());
    }

    public String name() { return name; }
    public double getBeatsValue() { return beatsValue; }
    public String getVisualIcon() { return visualIcon; }
    public String[] getAliases() { return aliases; }

    @Override
    public String toString() {
        return String.format("%s (%s -> %s beats)", name, visualIcon, beatsValue);
    }
}
