package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.song.models.TGMeasure;
import app.tuxguitar.song.models.TGTrack;

/** What the practice window reads of the cursor of the score: its track, its measure and its position (spec 011: a value, so that a test does not need the desktop {@code Caret}). */
final class CaretView {

	private final TGTrack track;
	private final TGMeasure measure;
	private final long position;

	CaretView(TGTrack track, TGMeasure measure, long position) {
		this.track = track;
		this.measure = measure;
		this.position = position;
	}

	TGTrack track() {
		return this.track;
	}

	TGMeasure measure() {
		return this.measure;
	}

	long position() {
		return this.position;
	}
}
