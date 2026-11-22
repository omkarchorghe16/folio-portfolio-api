package com.foliopro.api.model;

import java.util.Locale;

/**
 * The two watchlist markets and the spellings clients may send for them. Watchlists are stored with
 * the canonical values {@link #US} and {@link #INDIA}; "USA" is accepted as an alias of "US".
 */
public final class WatchlistMarket {
    public static final String US = "US";
    public static final String INDIA = "India";

    /** Regex for request validation: every accepted spelling, case-insensitive. */
    public static final String ACCEPTED_VALUES_PATTERN = "(?i)US|USA|India";

    private WatchlistMarket() {
    }

    /** Returns {@link #US} for "US"/"USA" and {@link #INDIA} for "India", ignoring case. */
    public static String normalize(String market) {
        if (market == null) {
            throw new IllegalArgumentException("Market must be US, USA or India");
        }
        return switch (market.trim().toUpperCase(Locale.ROOT)) {
            case "US", "USA" -> US;
            case "INDIA" -> INDIA;
            default -> throw new IllegalArgumentException("Market must be US, USA or India");
        };
    }

    public static boolean isUs(String market) {
        return US.equals(normalize(market));
    }
}
