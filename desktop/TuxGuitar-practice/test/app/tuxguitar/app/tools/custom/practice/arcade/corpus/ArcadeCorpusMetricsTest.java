package app.tuxguitar.app.tools.custom.practice.arcade.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;
import app.tuxguitar.song.models.TGChord;

/**
 * The profile and the selection of the real-score validation, on small scores written here (spec 003 FR-064, T060, T061, T065): they run in every build,
 * with no corpus, and show that what is measured is what the application builds, that the same input gives the same selection and that nothing is chosen
 * from a title.
 */
public class ArcadeCorpusMetricsTest {

	private static TrackMetrics metricsOf(SongFixture fixture) {
		PracticeSequence sequence = fixture.sequence();
		return TrackMetrics.of(fixture.song, fixture.track(), sequence, FollowTimeline.build(fixture.song, sequence, 100));
	}

	@Test
	public void aChordIsAnEventOfTheApplicationNotAnyBeatWithSeveralNotes() {
		SongFixture fixture = new SongFixture(2);
		fixture.quarter(0, 0, 1, 0);
		fixture.chord(0, 1, new int[] { 6, 5, 4 }, new int[] { 3, 5, 5 });
		fixture.chord(0, 2, new int[] { 6, 5 }, new int[] { 0, 2 });
		fixture.chord(0, 3, new int[] { 5, 4, 3, 2 }, new int[] { 3, 2, 0, 1 });
		fixture.quarter(1, 0, 2, 7);
		TGChord named = fixture.factory().newChord(6);
		named.setName("A5");
		fixture.track().getMeasure(0).getBeat(1).setChord(named);
		TrackMetrics m = metricsOf(fixture);
		assertEquals(5, m.events);
		assertEquals(2, m.notes);
		assertEquals(3, m.chords);
		assertEquals(3, m.longestChordRun);
		assertEquals(4, m.maxChordSize);
		assertEquals(1, m.namedChords);
		assertEquals(2, m.unnamedChords);
		assertEquals(6, m.strings);
		assertEquals(1, m.tracks);
		assertTrue(m.sequenceMonotonic);
		assertEquals(0.6, m.chordRatio(), 1e-9);
	}

	@Test
	public void thePeakIsTheBusiestSecondAndTheJumpTheLargestMoveOfTheFretCentre() {
		SongFixture fixture = new SongFixture(4);
		fixture.tempoChange(0, 240);
		for (int i = 0; i < 8; i++) {
			fixture.quarter(i / 4, i % 4, 1, i % 2 == 0 ? 2 : 14);
		}
		TrackMetrics m = metricsOf(fixture);
		assertEquals(4.0, m.peakPerSecond, 0.0, "four quarters a second at 240 a minute");
		assertEquals(12, m.maxJump);
		assertEquals(2, m.minFret);
		assertEquals(14, m.maxFret);
	}

	@Test
	public void repeatsAndTempoChangesAreCounted() {
		SongFixture fixture = new SongFixture(4);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(1, 0, 1, 1);
		fixture.repeat(0, 1, 2);
		fixture.tempoChange(2, 90);
		TrackMetrics m = metricsOf(fixture);
		assertEquals(2, m.repeats);
		assertEquals(1, m.tempoChanges);
	}

	@Test
	public void theTextFormOfTheMetricsRoundTrips() {
		SongFixture fixture = new SongFixture(2);
		fixture.chord(0, 0, new int[] { 6, 5, 4 }, new int[] { 3, 5, 5 });
		fixture.quarter(0, 1, 1, 12);
		TrackMetrics m = metricsOf(fixture);
		assertEquals(m.toTsv(), TrackMetrics.fromTsv(m.toTsv()).toTsv());
	}

	@Test
	public void theSelectionIsTheSameForTheSameInputWhateverTheOrderOfTheFilesAndNoTitleIsLookedAt() {
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < 300; i++) {
			names.add("folder" + (i % 40) + "/Artist" + (i % 25) + " - Song " + i + (i % 7 == 0 ? " (2)" : "") + (i % 2 == 0 ? ".gp3" : ".gp4"));
		}
		List<String> shuffled = new ArrayList<String>(names);
		java.util.Collections.reverse(shuffled);
		assertEquals(CorpusPipeline.sample(names, 50), CorpusPipeline.sample(shuffled, 50), "the sample depends on the paths alone");
		assertEquals(100, CorpusPipeline.sample(names, 50).size(), "fifty of each extension");
		assertEquals(CorpusPipeline.sample(names, 50), CorpusPipeline.sample(names, 50));
	}

	@Test
	public void noFolderOrArtistGivesMoreThanThreeCasesAndNoTitleTwice() {
		List<CorpusPipeline.FileRecord> records = new ArrayList<CorpusPipeline.FileRecord>();
		java.lang.reflect.Constructor<?> constructor;
		try {
			constructor = CorpusPipeline.FileRecord.class.getDeclaredConstructor(String.class, String.class);
			constructor.setAccessible(true);
			for (int i = 0; i < 120; i++) {
				String name = "dir" + (i % 4) + "/Band" + (i % 3) + " - Title " + (i % 20) + (i >= 20 ? " (" + (i / 20 + 1) + ")" : "") + (i % 2 == 0 ? ".gp3" : ".gp4");
				CorpusPipeline.FileRecord record = (CorpusPipeline.FileRecord) constructor.newInstance(name, i % 2 == 0 ? "gp3" : "gp4");
				record.status = CorpusPipeline.Status.OK;
				record.format = i % 2 == 0 ? "gp3" : "gp4";
				TrackMetrics m = new TrackMetrics();
				m.trackNumber = 1;
				m.events = 200;
				m.notes = 190;
				m.chords = 10;
				m.strings = 6;
				m.tracks = 2;
				record.tracks.add(m);
				records.add(record);
			}
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
		List<CorpusPipeline.Case> cases = CorpusPipeline.select(records);
		java.util.Map<String, Integer> byFolder = new java.util.HashMap<String, Integer>();
		java.util.Map<String, Integer> byArtist = new java.util.HashMap<String, Integer>();
		java.util.Set<String> titles = new java.util.HashSet<String>();
		for (CorpusPipeline.Case c : cases) {
			byFolder.merge(c.file.relative.substring(0, c.file.relative.indexOf('/')), 1, Integer::sum);
			byArtist.merge(c.file.relative.substring(c.file.relative.indexOf('/') + 1, c.file.relative.indexOf(" - ")), 1, Integer::sum);
			assertTrue(titles.add(c.file.relative.substring(c.file.relative.indexOf('/') + 1).replaceAll("\\s*\\(\\d+\\)", "").replaceAll("\\.gp[34]$", "")), "one arrangement per artist and title");
		}
		for (int n : byFolder.values()) {
			assertTrue(n <= 3, "folder " + byFolder);
		}
		for (int n : byArtist.values()) {
			assertTrue(n <= 3, "artist " + byArtist);
		}
		assertTrue(cases.size() >= 6, "cases are chosen: " + cases.size());
		assertEquals(CorpusPipeline.anonymousTable(cases), CorpusPipeline.anonymousTable(CorpusPipeline.select(records)));
	}

	@Test
	public void theCorpusIsOptionalAndTheReaderDetectsTheFormatFromTheContent() {
		// no environment variable here: the profiler test is skipped, and the reader says what it cannot read
		CorpusReader.Result result = new CorpusReader().read(new byte[] { 1, 2, 3, 4 });
		assertEquals(null, result.song);
		assertTrue(result.failure != null && !result.failure.isEmpty());
	}
}
