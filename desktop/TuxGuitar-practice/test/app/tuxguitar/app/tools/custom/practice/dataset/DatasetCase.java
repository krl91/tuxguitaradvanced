package app.tuxguitar.app.tools.custom.practice.dataset;

import java.nio.file.Path;
import java.util.Arrays;

/** One annotated recording of a public dataset: the pitches that sound in it and where it comes from. */
final class DatasetCase {

	enum Kind {
		SINGLE_NOTE, INTERVAL, CHORD
	}

	final String dataset;
	final String id;
	final Path audio;
	final int[] played;
	final Kind kind;
	/** Free text used to group results (guitar, effect, chord name...). */
	final String group;
	/** What was played (interval or chord name), for the breakdown of results. */
	final String label;

	DatasetCase(String dataset, String id, Path audio, int[] played, Kind kind, String group, String label) {
		this.dataset = dataset;
		this.id = id;
		this.audio = audio;
		this.played = played.clone();
		Arrays.sort(this.played);
		this.kind = kind;
		this.group = group;
		this.label = label;
	}
}
