package src.main.java.com.composer.core.domain.types;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public record Duration(String name, double beatsValue, String visualIcon, String... aliases) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final Duration WHOLE = new Duration("WHOLE", 4.0, "[ \uD834\uDD5D ] Whole Note", "4", "4.0", "whole", "semibreve");
    public static final Duration HALF = new Duration("HALF", 2.0, "[ \uD834\uDD5E ] Half Note", "2", "2.0", "half", "minim");
    public static final Duration QUARTER = new Duration("QUARTER", 1.0, "[ \uD834\uDD5F ] Quarter Note", "1", "1.0", "quarter", "crotchet");
    public static final Duration EIGHTH = new Duration("EIGHTH", 0.5, "[ \uD834\uDD60 ] Eighth Note", "0.5", "eighth", "8th", "quaver");
    public static final Duration SIXTEENTH = new Duration("SIXTEENTH", 0.25, "[ \uD834\uDD61 ] Sixteenth Note", "0.25", "sixteenth", "16th", "semiquaver");

    private static final Duration[] SYSTEM_DURATIONS = {WHOLE, HALF, QUARTER, EIGHTH, SIXTEENTH};
    private static final Map<String, Duration> ALIAS_MAP = new HashMap<>();

    static {
        for (Duration duration : SYSTEM_DURATIONS) {
            for (String alias : duration.aliases) {
                ALIAS_MAP.put(alias.toLowerCase(), duration);
            }
        }
    }

    public static Duration[] getSystemDurations() {
        return SYSTEM_DURATIONS;
    }

    public static Duration fromAlias(String token) {
        if (token == null) return null;
        return ALIAS_MAP.get(token.trim().toLowerCase());
    }

}
