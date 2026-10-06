package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.List;
import java.util.function.Function;

import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;

/**
 * The technique line of the practice window: for each written technique of the last finished note, what was heard (heard, not heard, uncertain,
 * or cannot be checked yet). Kept apart from the widgets so that it can be tested with every language of the application (spec 002 T140).
 */
final class TechniqueLine {

	/** The separator between two techniques of one note. */
	static final String SEPARATOR = " · ";

	private TechniqueLine() {
	}

	/** @param text the localized text of a key (the application's property lookup) */
	static String of(List<TechniqueResult> results, Function<String, String> text) {
		StringBuilder line = new StringBuilder();
		for( TechniqueResult result : results ) {
			line.append(line.length() > 0 ? SEPARATOR : "").append(text.apply("practice.technique.name." + result.getTechnique().name()))
					.append(": ").append(text.apply("practice.technique.verdict." + result.getVerdict().name()));
		}
		return line.length() == 0 ? " " : line.toString();
	}
}
