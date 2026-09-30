package app.tuxguitar.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The product name shown to the user. TuxGuitarAdvanced is a fork of TuxGuitar: texts inherited
 * from upstream (translations, help pages) still say "TuxGuitar", and {@link #apply} turns the
 * word into the fork's name when they are loaded, so those files stay identical to upstream
 * (simple synchronisation) while every screen shows the fork's name.
 *
 * <p>The upstream name is kept where it is an attribution or an identifier, never replaced in:
 * "fork of TuxGuitar" / "upstream TuxGuitar" (attribution), words and paths containing it
 * (URLs, file names, versions such as "TuxGuitar-1.6", "TuxGuitarAdvanced" itself).</p>
 */
public final class TGBranding {

	public static final String NAME = "TuxGuitarAdvanced";

	private static final Pattern UPSTREAM_NAME = Pattern.compile("(?<!fork of )(?<!upstream )(?<![\\w./@-])TuxGuitar(?!\\w|-\\d)");

	private TGBranding() {
	}

	/** Replaces the standalone word "TuxGuitar" by {@link #NAME}; other text is unchanged. */
	public static String apply(String text) {
		if( text == null || text.indexOf("TuxGuitar") < 0 ) {
			return text;
		}
		return UPSTREAM_NAME.matcher(text).replaceAll(Matcher.quoteReplacement(NAME));
	}
}
