package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeRenderEvent;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ChordNameResolver;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.song.models.TGChord;

/**
 * The identity of a chord as it reaches the painter (spec 003 FR-061, SC-018): the written name, the derived name or the list of notes is what the painter is
 * asked to draw in the header, the same text in every tier, in monochrome and in the fallback, and the score is not touched by finding it.
 */
public class ChordIdentityRenderTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	/** A written chord, an unnamed open C, an unnamed power chord, a set of notes no template describes, and a note. */
	private static SceneFixture fixture() {
		SongFixture song = new SongFixture(4);
		song.chord(1, 0, new int[] { 6, 5 }, new int[] { 3, 5 });
		song.chord(1, 1, new int[] { 5, 4, 3, 2, 1 }, new int[] { 3, 2, 0, 1, 0 });
		song.chord(1, 2, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		song.chord(1, 3, new int[] { 5, 4, 3 }, new int[] { 3, 0, 1 });
		song.quarter(2, 0, 2, 5);
		TGChord written = song.factory().newChord(6);
		written.setName("Gsus4/D");
		song.track().getMeasure(1).getBeat(0).setChord(written);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	private static String headerOf(Scene scene, int event) {
		return scene.forEvent(event, Role.CARTOUCHE_NAME).get(0).text();
	}

	@Test
	public void theHeaderShowsTheWrittenNameTheDerivedNameOrTheNotesAndNeverTheBareWord() {
		SceneFixture fixture = fixture();
		String[] expected = { "Gsus4/D", "C", "A5", "Notes: C\u2013D\u2013G\u266F" };
		assertEquals(ChordNameResolver.Source.WRITTEN, fixture.events.get(0).display().source());
		assertEquals(ChordNameResolver.Source.DERIVED, fixture.events.get(1).display().source());
		assertEquals(ChordNameResolver.Source.DERIVED, fixture.events.get(2).display().source());
		assertEquals(ChordNameResolver.Source.PITCH_SET, fixture.events.get(3).display().source());
		for (int event = 0; event < 4; event++) {
			// each chord in its turn is the one with the diagram: the earlier ones are judged
			long now = fixture.onset(event) - 600 * MS;
			Scene scene = fixture.scene(false).build(now, SCREEN, app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxesAccess.completedBefore(event, 5), 100);
			assertEquals(expected[event], headerOf(scene, event), "chord " + event);
		}
	}

	@Test
	public void theSameTextIsInEveryTierInMonochromeAndInTheTextsSentToThePainterByBothPaths() {
		SceneFixture fixture = fixture();
		for (int event = 0; event < 4; event++) {
			long now = fixture.onset(event) - 600 * MS;
			String reference = null;
			for (boolean mono : new boolean[] { false, true }) {
				for (QualityTier tier : QualityTier.values()) {
					Scene scene = fixture.scene(mono).build(now, SCREEN, app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxesAccess.completedBefore(event, 5), 100, tier);
					String text = headerOf(scene, event);
					reference = reference == null ? text : reference;
					assertEquals(reference, text, "chord " + event + " " + tier + (mono ? " mono" : ""));
					for (PainterRenderer renderer : new PainterRenderer[] { PaintRecorder.selected(), PaintRecorder.fallback() }) {
						PaintRecorder recorder = PaintRecorder.record(renderer, scene, tier);
						boolean sent = false;
						for (String sentText : recorder.texts) {
							sent |= sentText.startsWith(text + "@");
						}
						assertTrue(sent, "the painter was asked to draw \"" + text + "\" (" + renderer.name() + ", " + tier + ")");
					}
				}
			}
		}
	}

	@Test
	public void theTextIsKeptFromTheFirstReadableStateThroughImpactAndFeedback() {
		SceneFixture fixture = fixture();
		long look = app.tuxguitar.app.tools.custom.practice.arcade.view.CameraTrack.Settings.standard().lookAheadNanos;
		for (double share : new double[] { 0.55, 0.15, 0.0 }) {
			Scene scene = fixture.scene(false).build(fixture.onset(1) - (long) (share * look), SCREEN, app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxesAccess.completedBefore(1, 5), 100);
			assertEquals("C", headerOf(scene, 1), "at " + share);
		}
		Scene feedback = fixture.scene(false).build(fixture.onset(1) + 300 * MS, SCREEN, null, 100);
		assertTrue(feedback.forEvent(1, Role.CARTOUCHE_NAME).isEmpty() || headerOf(feedback, 1).equals("C"));
	}

	@Test
	public void aSingleNoteHasNoIdentityHeader() {
		SceneFixture fixture = fixture();
		assertEquals(null, fixture.events.get(4).display());
		Scene scene = fixture.scene(false).build(fixture.onset(4) - 500 * MS, SCREEN, app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxesAccess.completedBefore(4, 5), 100);
		assertTrue(scene.forEvent(4, Role.CARTOUCHE_NAME).isEmpty());
	}

	@Test
	public void findingTheNameChangesNothingInTheScoreAndTheSameCallsGiveTheSameDisplay() {
		SongFixture song = new SongFixture(2);
		song.chord(1, 0, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		app.tuxguitar.song.models.TGBeat beat = song.track().getMeasure(1).getBeat(0);
		assertFalse(beat.isChordBeat());
		List<ArcadeRenderEvent> first = build(song);
		assertFalse(beat.isChordBeat(), "no chord was written to the beat");
		assertEquals(null, beat.getChord());
		int measures = song.song.countMeasureHeaders();
		int tracks = song.song.countTracks();
		List<ArcadeRenderEvent> second = build(song);
		assertEquals(measures, song.song.countMeasureHeaders());
		assertEquals(tracks, song.song.countTracks());
		assertEquals(first.get(0).display(), second.get(0).display());
		assertEquals("A5", first.get(0).display().text());
	}

	private static List<ArcadeRenderEvent> build(SongFixture song) {
		app.tuxguitar.app.tools.custom.practice.model.PracticeSequence sequence = song.sequence();
		app.tuxguitar.app.tools.custom.practice.session.FollowTimeline timeline = app.tuxguitar.app.tools.custom.practice.session.FollowTimeline.build(song.song, sequence, 100);
		List<ArcadeRenderEvent> events = new ArrayList<ArcadeRenderEvent>();
		for (int i = 0; i < sequence.getEvents().size(); i++) {
			events.add(ArcadeRenderEvent.of(i, sequence.getEvents().get(i), timeline));
		}
		return events;
	}

	@Test
	public void theKeySignatureOfTheMeasureDecidesTheSpellingOfADerivedName() {
		SongFixture song = new SongFixture(2);
		// A#, D, F in the bass: B flat or A sharp major, by the key
		song.chord(1, 0, new int[] { 5, 4, 3 }, new int[] { 1, 3, 3 });
		song.track().getMeasure(1).setKeySignature(9);
		SceneFixture flat = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		assertEquals("Bb5", flat.events.get(0).display().text().replace("Bb", "Bb"), "flats: B flat, the fifth being F");
		assertTrue(flat.events.get(0).display().text().startsWith("Bb"));
		song.track().getMeasure(1).setKeySignature(2);
		SceneFixture sharp = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		assertTrue(sharp.events.get(0).display().text().startsWith("A#"), sharp.events.get(0).display().text());
	}
}
