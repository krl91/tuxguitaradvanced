package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.HOP;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LATENCY;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.audio.engine.technique.VibratoDetector;

/**
 * Each Follow value takes effect (spec 026, T022, SC-001): on scripted frames (no audio, fake clocks), every value of the three sections at one end of
 * its range changes what the session shows, frame by frame (each event's status, pitch, timing, offset, attempts, reason and technique results, the
 * extra notes, the state). The trials: notes on time, an attack whose pitch comes late, short notes, a second attack on a matched note, repeated notes
 * heard from their attack evidence at two tempos, a note heard from its evidence at the end of its window, a vibrato, bends at two tempos and past
 * their target.
 */
public class FollowTakesEffectTest {

	private static final int E4 = 64;
	private static final int D4 = 62;

	/** A scripted sound: a pitch (or none) from {@code start} for {@code duration}; its attack reported or not; its attack evidence from {@code evidenceFrom}. */
	private static final class Sound {
		final long start;
		final long duration;
		final DoubleUnaryOperator midiAt;
		final boolean reported;
		final long evidenceFrom;
		int evidenceMidi = E4;
		long evidenceNanos = 150 * MS;

		Sound(long start, long duration, DoubleUnaryOperator midiAt, boolean reported, long evidenceFrom) {
			this.start = start;
			this.duration = duration;
			this.midiAt = midiAt;
			this.reported = reported;
			this.evidenceFrom = evidenceFrom;
		}

		Sound evidence(int midi, long millis) {
			this.evidenceMidi = midi;
			this.evidenceNanos = millis * MS;
			return this;
		}
	}

	private StringBuilder log;

	@Test
	public void everyFollowValueChangesWhatIsShown() {
		String recommended = decisions(RecognitionSettings.defaults());
		assertTrue(recommended.contains("MATCHED"), "the recommended values match notes: " + recommended);
		List<String> withoutEffect = new ArrayList<String>();
		for( Section section : new Section[] { Section.FOLLOW_1, Section.FOLLOW_2, Section.FOLLOW_3 } ) {
			for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
				RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
				boolean changed = false;
				for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
					changed |= !decisions(RecognitionSettings.defaults().with(number, end)).equals(recommended);
				}
				if( !changed ) {
					withoutEffect.add(number.getKey());
				}
			}
		}
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change nothing shown");
	}

	private String decisions(RecognitionSettings settings) {
		this.log = new StringBuilder();
		FollowSession.FollowParameters p = FollowSession.FollowParameters.of(settings);
		// four notes on their beats
		FollowHarness h = harness("on time", fourNotes(), 100, p, false, null);
		run(h, sounds(sound(h.target(0), 300, E4), sound(h.target(1), 300, E4 + 1), sound(h.target(2), 300, E4 + 3), sound(h.target(3), 300, E4 + 5)));
		// an attack with no pitch, its pitch coming later without an attack of its own
		for( long late : new long[] { 250, 600 } ) {
			h = harness("pitch " + late, fourNotes(), 100, p, false, null);
			run(h, sounds(new Sound(h.target(0), 40 * MS, s -> Double.NaN, true, -1), new Sound(h.target(0) + late * MS, 300 * MS, s -> E4, false, -1)));
		}
		// short notes, then silence
		for( long held : new long[] { 25, 60 } ) {
			h = harness("short " + held, fourNotes(), 100, p, false, null);
			run(h, sounds(sound(h.target(0), held, E4)));
		}
		// a second attack on a note already matched
		for( long after : new long[] { 60, 200 } ) {
			h = harness("again " + after, fourNotes(), 100, p, false, null);
			run(h, sounds(sound(h.target(0), 500, E4), new Sound(h.target(0) + after * MS, 300 * MS, s -> E4, true, -1)));
		}
		// repeated notes heard from their attack evidence only (no pitch), the evidence of the second one late or early against its reported attack
		for( int tempo : new int[] { 60, 30 } ) {
			for( long delay : new long[] { 90, -20, -60 } ) {
				h = harness("repeated " + tempo + " " + delay, repeated(tempo), 100, p, true, null);
				run(h, sounds(new Sound(h.target(0), 150 * MS, s -> Double.NaN, true, h.target(0) + 20 * MS),
						new Sound(h.target(1) + 10 * MS, 150 * MS, s -> Double.NaN, true, h.target(1) + (10 + delay) * MS)));
			}
		}
		// the same, when the session waits for the detector to report the attack before it takes the time of the evidence
		for( long delay : new long[] { -20, -60 } ) {
			h = harness("waits " + delay, repeated(60), 100, p, true, null);
			h.session.setWaitsForReportedAttack(true);
			run(h, sounds(new Sound(h.target(0), 150 * MS, s -> Double.NaN, true, h.target(0) + 20 * MS),
					new Sound(h.target(1) + 10 * MS, 150 * MS, s -> Double.NaN, true, h.target(1) + (10 + delay) * MS)));
		}
		// a repeated note struck at the end of its window, its evidence confirmed after the window closed
		h = harness("closed", repeated(60), 100, p, true, null);
		run(h, sounds(new Sound(h.target(0), 150 * MS, s -> Double.NaN, true, h.target(0) + 20 * MS),
				new Sound(h.target(1) + 380 * MS, 150 * MS, s -> Double.NaN, true, h.target(1) + 520 * MS)));
		// a repeated note struck late in its window, then an attack reported just outside it, before the evidence of the first is confirmed
		h = harness("foreign", repeated(60), 100, p, true, null);
		run(h, sounds(new Sound(h.target(0), 150 * MS, s -> Double.NaN, true, h.target(0) + 20 * MS),
				new Sound(h.target(1) + 380 * MS, 40 * MS, s -> Double.NaN, true, h.target(1) + 470 * MS),
				new Sound(h.target(1) + 420 * MS, 40 * MS, s -> Double.NaN, true, -1)));
		// F4 heard from its evidence only, late in its window, nothing reported
		for( long offset : new long[] { 80, 120, 160, 190 } ) {
			h = harness("evidence " + offset, fourNotes(), 100, p, true, null);
			run(h, sounds(new Sound(h.target(1) + offset * MS, 150 * MS, s -> Double.NaN, false, h.target(1) + offset * MS).evidence(E4 + 1, 150)));
		}
		// F4's evidence beginning well before its window and lasting into it
		for( long before : new long[] { 150, 350 } ) {
			h = harness("early evidence " + before, fourNotes(), 100, p, true, null);
			run(h, sounds(new Sound(h.target(1) - before * MS, 150 * MS, s -> Double.NaN, false, h.target(1) - before * MS).evidence(E4 + 1, before + 100)));
		}
		// a vibrato, held
		SongFixture vibrato = new SongFixture(2);
		vibrato.quarter(0, 0, 1, 0).getEffect().setVibrato(true);
		vibrato.quarter(1, 0, 1, 1);
		h = harness("vibrato", vibrato, 100, p, false, new TechniqueJudge(TechniqueCatalog.of(new VibratoDetector(VibratoDetector.Parameters.defaults()), true, "test"), true));
		run(h, sounds(new Sound(h.target(0), 4500 * MS, s -> E4 + 0.3 * Math.sin(2.0 * Math.PI * 5.5 * s), true, -1)));
		// bends at two tempos, made and past their target
		TechniqueJudge bends = new TechniqueJudge(TechniqueCatalog.of(new BendDetector(BendDetector.Parameters.defaults()), true, "test"), true);
		for( int tempo : new int[] { 120, 20 } ) {
			h = harness("bend " + tempo, bendThenNote(tempo), 100, p, false, bends);
			run(h, sounds(new Sound(h.target(0), 4500 * MS, s -> D4 + 2.0 * Math.min(1.0, Math.max(0.0, (s - 0.15) / 0.25)), true, -1)));
		}
		// the base note for a moment, then straight past the target of the bend: the run goes on along the path, or breaks
		for( double past : new double[] { 0.3, 0.7, 2.3 } ) {
			h = harness("past " + past, bendThenNote(120), 100, p, false, bends);
			run(h, sounds(new Sound(h.target(0), 600 * MS, s -> s < 0.05 ? D4 : D4 + 2.0 + past, true, -1)));
		}
		return this.log.toString();
	}

	// ---------------------------------------------------------------- the runs

	private FollowHarness harness(String name, SongFixture fixture, int percent, FollowSession.FollowParameters parameters, boolean evidence, TechniqueJudge judge) {
		FollowHarness harness = new FollowHarness(fixture, percent, LATENCY);
		harness.session.setFollowParameters(parameters);
		if( evidence ) {
			harness.session.setPolyphonicAcceptance(() -> new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		}
		if( judge != null ) {
			harness.session.setTechniqueJudge(judge);
		}
		harness.session.start();
		this.log.append('\n').append(name).append(':');
		return harness;
	}

	/** Every hop until the run is over; what the snapshot shows is written down each time it changes, with the hop it changed on. */
	private void run(FollowHarness h, List<Sound> sounds) {
		long end = T0 + FollowHarness.LEAD + h.timeline.durationNanos() + 2_000 * MS;
		String shown = null;
		int hop = 0;
		for( long wall = T0 ; wall < end ; wall += HOP, hop ++ ) {
			Sound sounding = null;
			boolean onset = false;
			AttackEvidence evidence = null;
			int evidenceMidi = E4 + 1;
			for( Sound sound : sounds ) {
				if( sound.start <= wall && wall < sound.start + sound.duration ) {
					sounding = sound;
				}
				onset |= (sound.reported && wall == sound.start + LATENCY);
				if( sound.evidenceFrom >= 0 && wall >= sound.evidenceFrom && wall < sound.evidenceFrom + sound.evidenceNanos ) {
					evidence = evidence(sound.evidenceMidi, 1.0);
				}
			}
			double midi = (sounding != null ? sounding.midiAt.applyAsDouble((wall - sounding.start) / 1e9) : Double.NaN);
			double hz = (Double.isNaN(midi) ? Double.NaN : FollowHarness.hz(midi));
			AnalysisFrame frame = new AnalysisFrame(wall - T0, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, hz, Double.isNaN(hz) ? 0.0 : 0.95, onset, null,
					null, null, evidence != null ? evidence : new AttackEvidence(new int[] { evidenceMidi }, new double[] { 0.0 }, 0.0));
			FollowSession.Snapshot snapshot = h.session.onFrame(frame);
			String now = describe(snapshot);
			if( !now.equals(shown) ) {
				shown = now;
				this.log.append(' ').append(hop).append('>').append(now).append(';');
			}
		}
	}

	private static String describe(FollowSession.Snapshot snapshot) {
		StringBuilder s = new StringBuilder();
		s.append(snapshot.state()).append(" extra ").append(snapshot.counts().extra());
		for( FollowSession.EventResult result : snapshot.results() ) {
			s.append(" [").append(result.status()).append(' ').append(result.timing()).append(' ').append(result.offsetNanos() / MS).append(' ')
					.append(result.attempts()).append(' ').append(result.reason());
			for( TechniqueResult technique : result.techniques() ) {
				s.append(' ').append(technique.getTechnique()).append('=').append(technique.getVerdict()).append(technique.getMeasurements());
			}
			s.append(']');
		}
		return s.toString();
	}

	private static Sound sound(long start, long durationMillis, int midi) {
		return new Sound(start, durationMillis * MS, s -> midi, true, -1);
	}

	private static List<Sound> sounds(Sound... sounds) {
		return java.util.Arrays.asList(sounds);
	}

	/** The attack evidence of one pitch: its harmonics grown at their exact places (so that its tuning is measurable), 10 Hz bins. */
	private static AttackEvidence evidence(int midi, double amplitude) {
		double f0 = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
		double[] growth = new double[2000];
		for( int h = 1 ; h <= 8 ; h ++ ) {
			int bin = (int) Math.round(f0 * h / 10.0);
			if( bin > 0 && bin < growth.length - 1 ) {
				growth[bin] = amplitude / h;
				growth[bin - 1] = amplitude / h / 3;
				growth[bin + 1] = amplitude / h / 3;
			}
		}
		return new AttackEvidence(new int[] { midi }, new double[] { amplitude }, 0.3, growth, 10.0);
	}

	// ---------------------------------------------------------------- the scores

	/** E4, F4, G4, A4 on string 1. */
	private static SongFixture fourNotes() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 1, 1, 1);
		fixture.quarter(0, 2, 1, 3);
		fixture.quarter(0, 3, 1, 5);
		return fixture;
	}

	/** Two E4 in a row at {@code tempo} quarters a minute. */
	private static SongFixture repeated(int tempo) {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 1, 1, 0);
		fixture.tempoChange(0, tempo);
		return fixture;
	}

	/** D4 bent a whole tone (string 3, fret 7), then A4 two beats later, at {@code tempo}. */
	private static SongFixture bendThenNote(int tempo) {
		SongFixture fixture = new SongFixture(2);
		fixture.bend(0, 0, 3, 7, 2);
		fixture.quarter(1, 0, 3, 14);
		fixture.tempoChange(0, tempo);
		return fixture;
	}
}
