package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneLabels;

/** The words of the scene in the learner's language, from the keys {@code arcade.label.*} of the language files (spec 003 T056). */
public final class ArcadeSceneLabels implements SceneLabels {

	private final ArcadeTexts texts;

	public ArcadeSceneLabels(ArcadeTexts texts) {
		this.texts = texts;
	}

	@Override
	public String verdict(ArcadeVerdict verdict) {
		return this.texts.get("arcade.label.verdict." + verdict.name());
	}

	@Override
	public String pitchSet(String notes) {
		return this.texts.get("arcade.label.pitchset", notes);
	}

	@Override
	public String openString() {
		return this.texts.get("arcade.label.open");
	}

	@Override
	public String automatic() {
		return this.texts.get("arcade.label.automatic");
	}

	@Override
	public String unsupported() {
		return this.texts.get("arcade.label.unsupported");
	}

	@Override
	public String missing(String names) {
		return this.texts.get("arcade.label.missing", names);
	}

	@Override
	public String notCheckedSeparately() {
		return this.texts.get("arcade.label.not-checked");
	}

	@Override
	public String streak(int streak) {
		return this.texts.get("arcade.label.streak", Integer.toString(streak));
	}

	@Override
	public String speed(int percent) {
		return this.texts.get("arcade.label.speed", Integer.toString(percent));
	}

	@Override
	public String nextNote(int string, int fret) {
		return this.texts.get("arcade.label.next.note", Integer.toString(string), Integer.toString(fret));
	}

	@Override
	public String nextChord(String name) {
		return this.texts.get("arcade.label.next.chord", name);
	}
}
