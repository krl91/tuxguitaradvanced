package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * The amount of a bend as it is written on the picture (spec 003 FR-077): in steps, a step being a whole tone, with quarters (¼, ½, ¾, full, 1¼, 1½, 2...),
 * *pre* before it for a pre-bend and an arrow down after it for a release.
 */
public final class BendLabel {

	private static final String[] QUARTERS = { "", "¼", "½", "¾" };

	private BendLabel() {
	}

	public static String of(BendMark mark, SceneLabels labels) {
		int quarters = (int) Math.round(mark.peakSemitones() / 2.0 * 4.0);
		quarters = Math.max(1, quarters);
		int whole = quarters / 4;
		int rest = quarters % 4;
		String amount;
		if (whole == 0) {
			amount = QUARTERS[rest];
		} else if (whole == 1 && rest == 0) {
			amount = labels.bendFull();
		} else {
			amount = Integer.toString(whole) + QUARTERS[rest];
		}
		return (mark.startsBent() ? labels.bendPre() + " " : "") + amount + (mark.releases() ? "\u2193" : "");
	}
}
