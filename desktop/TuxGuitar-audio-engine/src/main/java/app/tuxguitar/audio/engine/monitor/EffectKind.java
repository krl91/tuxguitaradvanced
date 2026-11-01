package app.tuxguitar.audio.engine.monitor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The pedals of the first set and their knobs (spec 022 FR-005, data-model "EffectKind"). */
public enum EffectKind {

	GATE("gate", Section.BEFORE, new Knob("threshold", -90, -20, "dB", -60), new Knob("release", 20, 1000, "ms", 150)),
	COMPRESSOR("compressor", Section.BEFORE, new Knob("sustain", 0, 10, "", 5), new Knob("attack", 1, 100, "ms", 10), new Knob("level", 0, 10, "", 5)),
	OVERDRIVE("overdrive", Section.BEFORE, new Knob("drive", 0, 10, "", 5), new Knob("tone", 0, 10, "", 5), new Knob("level", 0, 10, "", 5)),
	DISTORTION("distortion", Section.BEFORE, new Knob("gain", 0, 10, "", 6), new Knob("tone", 0, 10, "", 5), new Knob("level", 0, 10, "", 5)),
	CHORUS("chorus", Section.AFTER, new Knob("rate", 0.1, 5, "Hz", 0.8), new Knob("depth", 0, 10, "", 5), new Knob("mix", 0, 100, "%", 50)),
	ECHO("echo", Section.AFTER, new Knob("time", 50, 1500, "ms", 380), new Knob("repeats", 0, 10, "", 3), new Knob("tone", 0, 10, "", 5),
			new Knob("mix", 0, 100, "%", 30)),
	REVERB("reverb", Section.AFTER, new Knob("size", 0, 10, "", 5), new Knob("damping", 0, 10, "", 5), new Knob("mix", 0, 100, "%", 25));

	/** Where a pedal sits: before the amp or after it. A pedal moves only within its section. */
	public enum Section {
		BEFORE,
		AFTER
	}

	private final String textName;
	private final Section section;
	private final List<Knob> knobs;

	EffectKind(String textName, Section section, Knob... knobs) {
		this.textName = textName;
		this.section = section;
		this.knobs = Collections.unmodifiableList(Arrays.asList(knobs));
	}

	/** The name used in the chain's text (research R6) and in the text keys. */
	public String textName() {
		return this.textName;
	}

	public Section section() {
		return this.section;
	}

	public List<Knob> knobs() {
		return this.knobs;
	}

	/** The knob of that key, or null. */
	public Knob knob(String key) {
		for( Knob knob : this.knobs ) {
			if( knob.key().equals(key) ) {
				return knob;
			}
		}
		return null;
	}

	int knobIndex(String key) {
		for( int i = 0 ; i < this.knobs.size() ; i++ ) {
			if( this.knobs.get(i).key().equals(key) ) {
				return i;
			}
		}
		return -1;
	}

	/** The kind of that text name, or null. */
	public static EffectKind ofText(String name) {
		for( EffectKind kind : values() ) {
			if( kind.textName.equals(name) ) {
				return kind;
			}
		}
		return null;
	}
}
