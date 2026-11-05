package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.app.tools.custom.practice.session.AttackCandidates;
import app.tuxguitar.app.tools.custom.practice.session.ChordMatcher;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.analysis.ChordEvidence;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * The diagnostic of the owner's chords (spec 024, US3, FR-010, research R1): for each take judged against its own chord and against its control (the same
 * pairing as {@code OwnerChordsBenchmarkTest}), what the two acceptance paths saw, the cause of a refusal or of a wrong acceptance, and which single
 * relaxation (research R3) would flip the outcome. Levers L2 and L3 are real settings and run the real session; L1 (one string short) and L4 (clipped chord
 * frames judged) do not exist yet, so the chord check is simulated frame by frame with them, from the same evidence {@code ChordMatcher} reads, and the
 * simulation of the chord check without any lever is reported next to the real outcome as its check. Run with {@code -Dgroups=dataset -DexcludedGroups=
 * -Dtest=OwnerChordsDiagnosticTest} and {@code -Dtuxguitar.recognition.engine=java|native}; the report goes to
 * {@code target/dataset-benchmark/owner-chords-diagnostic-<engine>.txt}. It asserts nothing about the figures.
 */
@Tag("dataset")
public class OwnerChordsDiagnosticTest {

	private static final Path BASE = Paths.get("../../specs/004-attack-difference-recognition/benchmark");
	private static final int[] OPEN_STRINGS = { 64, 59, 55, 50, 45, 40 };
	/** The silence a learner leaves after pressing Start, as {@code DatasetJudge} adds it. */
	private static final double LEAD_SECONDS = 0.5;
	/** The frames looked at: from the start of the take to its end (the session itself judges every frame of the take). */
	private static final double WINDOW_END_SECONDS = Double.MAX_VALUE;
	/** The run that tolerates gaps: the confirmation's frames among this many in a row (8 of 12, about 64 ms). */
	private static final int GAP_SPAN = 12;

	private static final class Take {
		final String folder;
		final String file;
		final int[] pitches;
		final RecordedAudio audio;

		Take(String folder, String file, int[] pitches, RecordedAudio audio) {
			this.folder = folder;
			this.file = file;
			this.pitches = pitches;
			this.audio = audio;
		}

		String label() {
			return this.folder + "/" + this.file;
		}
	}

	private static List<Take> takes() throws IOException {
		List<Take> takes = new ArrayList<Take>();
		for( Path folder : new Path[] { BASE.resolve("owner-chords"), BASE.resolve("owner-chords-2") } ) {
			Path labels = folder.resolve("labels.csv");
			if( !Files.isRegularFile(labels) ) {
				continue;
			}
			List<String> lines = Files.readAllLines(labels, StandardCharsets.UTF_8);
			for( String line : lines.subList(1, lines.size()) ) {
				String[] cells = line.split(",", 2);
				if( cells.length < 2 || cells[1].trim().isEmpty() ) {
					continue;
				}
				int[] pitches = Arrays.stream(cells[1].trim().split(" ")).mapToInt(Integer::parseInt).toArray();
				if( DatasetJudge.strings(pitches) == null ) {
					continue;
				}
				RecordedAudio audio = RecordedAudio.load(folder.resolve(cells[0]));
				if( audio != null ) {
					takes.add(new Take(folder.getFileName().toString(), cells[0], pitches, audio));
				}
			}
		}
		return takes;
	}

	/** A session expecting {@code expected}, as {@code DatasetJudge.accepts} builds it, with the given settings and chord matcher. */
	private static final class Run {
		final PracticeSession session;
		final PracticeSequence sequence;

		Run(int[] expected, RecognitionSettings settings, ChordMatcher matcher) {
			int[] strings = DatasetJudge.strings(expected);
			SongFixture fixture = new SongFixture(1);
			for( int i = 0 ; i < expected.length ; i ++ ) {
				fixture.quarter(0, 0, strings[i], expected[i] - OPEN_STRINGS[strings[i] - 1]);
			}
			this.sequence = fixture.sequence();
			PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
					PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
			selection.accept();
			PracticeParameters parameters = PracticeParameters.of(settings);
			this.session = new PracticeSession(selection, this.sequence, parameters.noteInterpreter(), parameters.tolerance(), parameters.stabilityNanos(),
					parameters.articulationLatencyNanos(), matcher != null ? matcher : new ChordMatcher(parameters.chordParameters()), null);
			this.session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
			this.session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
			this.session.onCommand(PracticeCommand.START);
		}

		PracticeEvent event() {
			for( PracticeEvent event : this.sequence.getEvents() ) {
				if( event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD || event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ) {
					return event;
				}
			}
			return null;
		}

		double acceptedAt = Double.NaN;

		boolean accepts(List<AnalysisFrame> frames) {
			for( AnalysisFrame frame : frames ) {
				if( this.session.onFrame(frame).getAcceptedCount() > 0 ) {
					this.acceptedAt = frame.getTimestampNanos() / 1e9 - LEAD_SECONDS;
					return true;
				}
			}
			return false;
		}
	}

	private static List<AnalysisFrame> frames(RecordedAudio audio, int[] expected, RecognitionSettings settings, PracticeSequence sequence) {
		float[] samples = new float[audio.samples.length + audio.sampleRate / 2];
		System.arraycopy(audio.samples, 0, samples, audio.sampleRate / 2, audio.samples.length);
		return OfflineAnalysis.analyze(samples, audio.sampleRate, settings, expected.length > 1, AttackCandidates.of(sequence));
	}

	private static boolean accepts(RecordedAudio audio, int[] expected, RecognitionSettings settings, ChordMatcher matcher) {
		return acceptance(audio, expected, settings, matcher) >= 0;
	}

	/** The time of the acceptance in seconds from the start of the take, or -1 when refused. */
	private static double acceptance(RecordedAudio audio, int[] expected, RecognitionSettings settings, ChordMatcher matcher) {
		Run run = new Run(expected, settings, matcher);
		return run.accepts(frames(audio, expected, settings, run.sequence)) ? run.acceptedAt : -1;
	}

	/** What one judgement saw, frame by frame, inside the window. */
	private static final class Look {
		boolean accepted;
		double acceptedAt;
		boolean attackOnly;
		boolean chordSimulated;
		boolean oneShort;
		boolean clipped;
		boolean oneShortClipped;
		boolean oneShortAny;
		boolean twoShort;
		boolean gapped;
		boolean oneShortGapped;
		boolean evidence25;
		boolean evidence20;
		boolean confirm30;
		double clipShare;
		int longestRun;
		int fewestMissing = Integer.MAX_VALUE;
		double bestShare;
		double bestPeak;
		double bestBalance;
		final Map<Integer, String> pitches = new LinkedHashMap<Integer, String>();
		String cause;
	}

	private static Look look(RecordedAudio audio, int[] expected, RecognitionSettings settings) {
		Look look = new Look();
		look.acceptedAt = acceptance(audio, expected, settings, null);
		look.accepted = look.acceptedAt >= 0;
		// the attack path alone: a chord check that can never be complete (every pitch unverifiable)
		look.attackOnly = accepts(audio, expected, settings, new ChordMatcher(new ChordMatcher.Parameters(99, 1.0, 1, 60_000_000_000L)));
		look.evidence25 = accepts(audio, expected, settings.with(RecognitionParameters.CHORD_EVIDENCE, 0.25), null);
		look.evidence20 = accepts(audio, expected, settings.with(RecognitionParameters.CHORD_EVIDENCE, 0.20), null);
		look.confirm30 = accepts(audio, expected, settings.with(RecognitionParameters.CHORD_CONFIRM, 30.0), null);

		Run run = new Run(expected, settings, null);
		PracticeEvent event = run.event();
		int[] required = event.getRequiredPitches();
		int[] tolerated = event.getToleratedPitches();
		ChordMatcher.Parameters chord = PracticeParameters.of(settings).chordParameters();
		List<AnalysisFrame> frames = frames(audio, expected, settings, run.sequence);
		int inWindow = 0;
		int clippedFrames = 0;
		int[] runs = new int[6]; // default, one string short, clipped judged, both, one string short with no clear partial, two strings short
		int[] longest = new int[6];
		java.util.ArrayDeque<boolean[]> recent = new java.util.ArrayDeque<boolean[]>(); // the last frames, for the run that tolerates gaps
		int[] bestGapped = new int[2]; // complete frames among the last GAP_SPAN: default, one string short
		Map<Integer, double[]> best = new LinkedHashMap<Integer, double[]>(); // midi -> {unique, clear, fraction}
		int[] separable = PolyphonicAcceptance.separable(expected);
		for( AnalysisFrame frame : frames ) {
			double seconds = frame.getTimestampNanos() / 1e9;
			if( seconds < LEAD_SECONDS - 0.05 || seconds > WINDOW_END_SECONDS ) {
				continue;
			}
			inWindow ++;
			boolean clipping = frame.getLevelState() == AnalysisFrame.LevelState.CLIPPING;
			boolean usable = frame.getLevelState() == AnalysisFrame.LevelState.SUITABLE || frame.getLevelState() == AnalysisFrame.LevelState.TOO_HIGH;
			clippedFrames += clipping ? 1 : 0;
			AttackEvidence attack = frame.getAttackEvidence();
			if( attack != null && attack.getTotal() > 0 && attack.getMaximum() > 0 ) {
				double sum = 0;
				double strongest = 0;
				for( int pitch : expected ) {
					sum += attack.amplitudeOf(pitch);
					strongest = Math.max(strongest, attack.amplitudeOf(pitch));
				}
				double weakest = Double.MAX_VALUE;
				double strongestOwn = 0;
				for( int pitch : separable ) {
					weakest = Math.min(weakest, attack.amplitudeOf(pitch));
					strongestOwn = Math.max(strongestOwn, attack.amplitudeOf(pitch));
				}
				double reference = Math.max(strongest, strongestOwn);
				look.bestShare = Math.max(look.bestShare, sum / attack.getTotal());
				look.bestPeak = Math.max(look.bestPeak, strongest / attack.getMaximum());
				if( separable.length > 1 && reference > 0 ) {
					look.bestBalance = Math.max(look.bestBalance, weakest / reference);
				}
			}
			if( frame.getSpectrum() == null ) {
				Arrays.fill(runs, 0);
				recent.clear();
				continue;
			}
			ChordEvidence evidence = ChordEvidence.evaluate(frame.getSpectrum(), required, tolerated);
			int verifiable = 0;
			int heard = 0;
			int missing = 0;
			int missingWithAClearPartial = 0;
			for( ChordEvidence.Pitch pitch : evidence.getPitches() ) {
				double[] kept = best.computeIfAbsent(pitch.getMidi(), key -> new double[] { 0, -1, -1 });
				kept[0] = pitch.getUniquePartials();
				if( pitch.getUniqueClearPartials() > kept[1] || (pitch.getUniqueClearPartials() == kept[1] && pitch.getUniquePresentFraction() > kept[2]) ) {
					kept[1] = pitch.getUniqueClearPartials();
					kept[2] = pitch.getUniquePresentFraction();
				}
				if( pitch.getUniquePartials() < chord.minClearPartials ) {
					continue;
				}
				verifiable ++;
				if( pitch.getUniqueClearPartials() >= chord.minClearPartials && pitch.getUniquePresentFraction() >= chord.minClearFraction ) {
					heard ++;
				} else {
					missing ++;
					missingWithAClearPartial += pitch.getUniqueClearPartials() >= 1 ? 1 : 0;
				}
			}
			if( usable || clipping ) {
				look.fewestMissing = Math.min(look.fewestMissing, missing);
			}
			boolean complete = heard > 0 && missing == 0;
			boolean oneShort = complete || (verifiable >= 3 && missing == 1 && missingWithAClearPartial == 1);
			boolean oneShortAny = complete || (verifiable >= 3 && missing == 1);
			boolean twoShort = complete || (verifiable >= 4 && missing <= 2);
			boolean[] passes = { usable && complete, usable && oneShort, (usable || clipping) && complete, (usable || clipping) && oneShort, usable && oneShortAny,
					usable && twoShort };
			recent.addLast(new boolean[] { usable && complete, usable && oneShort });
			if( recent.size() > GAP_SPAN ) {
				recent.removeFirst();
			}
			for( int g = 0 ; g < bestGapped.length ; g ++ ) {
				int count = 0;
				for( boolean[] r : recent ) {
					count += r[g] ? 1 : 0;
				}
				bestGapped[g] = Math.max(bestGapped[g], count);
			}
			for( int i = 0 ; i < runs.length ; i ++ ) {
				runs[i] = passes[i] ? runs[i] + 1 : 0;
				longest[i] = Math.max(longest[i], runs[i]);
			}
		}
		look.longestRun = longest[0];
		look.chordSimulated = longest[0] >= chord.stableHops;
		look.oneShort = look.attackOnly || longest[1] >= chord.stableHops;
		look.clipped = look.attackOnly || longest[2] >= chord.stableHops;
		look.oneShortClipped = look.attackOnly || longest[3] >= chord.stableHops;
		look.oneShortAny = look.attackOnly || longest[4] >= chord.stableHops;
		look.twoShort = look.attackOnly || longest[5] >= chord.stableHops;
		look.gapped = look.attackOnly || bestGapped[0] >= chord.stableHops;
		look.oneShortGapped = look.attackOnly || bestGapped[1] >= chord.stableHops;
		look.clipShare = inWindow == 0 ? 0 : (double) clippedFrames / inWindow;
		for( Map.Entry<Integer, double[]> entry : best.entrySet() ) {
			double[] v = entry.getValue();
			String state = v[0] < chord.minClearPartials ? "unverifiable"
					: (v[1] >= chord.minClearPartials && v[2] >= chord.minClearFraction ? "heard" : "missing");
			look.pitches.put(entry.getKey(), String.format("%s(%d/%d,%.2f)", state, (int) v[1], (int) v[0], Math.max(0, v[2])));
		}
		if( look.clipShare > 0.5 ) {
			look.cause = "clipped";
		} else if( look.fewestMissing == 1 ) {
			look.cause = "one string short";
		} else if( look.evidence20 || look.evidence25 ) {
			look.cause = "weak evidence";
		} else if( look.longestRun > 0 && look.longestRun < chord.stableHops ) {
			look.cause = "too short";
		} else {
			look.cause = "other";
		}
		return look;
	}

	private static String line(Take take, int[] expected, Look look, boolean own) {
		String path = !look.accepted ? "-" : (look.attackOnly ? "attack" : "chord check");
		String outcome = own ? (look.accepted ? "ACCEPTED" : "REFUSED") : (look.accepted ? "WRONGLY ACCEPTED" : "refused")
				+ (sameNotes(take.pitches, expected) ? " (same notes)" : "");
		return String.format("%-26s %-22s expecting %-22s %-16s path=%-11s cause=%-16s at=%5.2fs clip=%3.0f%% run=%2d attack(share %.2f peak %.2f balance %.3f) "
				+ "flips: L1=%s L4=%s L1+L4=%s ev0.25=%s ev0.20=%s confirm30=%s sim=%s | %s",
				take.label(), Arrays.toString(take.pitches), Arrays.toString(expected), outcome, path, look.accepted == own || !own ? "-" : look.cause,
				look.acceptedAt, 100 * look.clipShare, look.longestRun, look.bestShare, look.bestPeak, look.bestBalance, flag(look.oneShort, look.accepted),
				flag(look.clipped, look.accepted), flag(look.oneShortClipped, look.accepted), flag(look.evidence25, look.accepted), flag(look.evidence20, look.accepted),
				flag(look.confirm30, look.accepted), (look.chordSimulated || look.attackOnly) == look.accepted ? "ok" : "DIFFERS", look.pitches);
	}

	/**
	 * True when one chord's note names (pitch classes) are all among the other's: the same chord in another voicing, a string more or less, or an octave more
	 * or less, which audio cannot always tell apart (an octave is a partial of the lower note).
	 */
	static boolean sameNotes(int[] played, int[] expected) {
		java.util.Set<Integer> a = new java.util.TreeSet<Integer>();
		java.util.Set<Integer> b = new java.util.TreeSet<Integer>();
		for( int pitch : played ) {
			a.add(pitch % 12);
		}
		for( int pitch : expected ) {
			b.add(pitch % 12);
		}
		return a.containsAll(b) || b.containsAll(a);
	}

	private static String flag(boolean lever, boolean now) {
		return lever == now ? "." : (lever ? "ACCEPT" : "refuse");
	}

	@Test
	public void eachRefusedChordAndEachWrongAcceptanceHasACause() throws Exception {
		List<Take> takes = takes();
		assumeTrue(!takes.isEmpty(), "the owner's recordings are not here");
		RecognitionSettings settings = RecognitionSettings.defaults();
		String engine = PitchEngineCatalog.resolveKind(settings).name().toLowerCase();
		StringBuilder report = new StringBuilder("Owner chords diagnostic (spec 024 US3), engine " + engine + "\n");
		report.append("flips: '.' = the lever leaves the outcome as it is; ACCEPT = the lever would accept; sim = the simulated chord check (or the attack path) gives the real outcome\n\n");
		Map<String, int[]> causes = new LinkedHashMap<String, int[]>(); // cause -> {refused, wrong}
		int[] recovered = new int[LEVERS.length]; // refusals turned into acceptances, per lever
		int[] added = new int[LEVERS.length]; // refused controls turned into wrong acceptances, per lever
		int[] addedOther = new int[LEVERS.length]; // of those, the ones whose notes are not those of the chord played
		int accepted = 0;
		int wrong = 0;
		int differs = 0;
		int sameNotesWrong = 0;
		for( int i = 0 ; i < takes.size() ; i ++ ) {
			Take take = takes.get(i);
			Look own = look(take.audio, take.pitches, settings);
			accepted += own.accepted ? 1 : 0;
			report.append(line(take, take.pitches, own, true)).append('\n');
			differs += (own.chordSimulated || own.attackOnly) == own.accepted ? 0 : 1;
			if( !own.accepted ) {
				causes.computeIfAbsent(own.cause, key -> new int[2])[0]++;
				count(recovered, own);
			}
			for( int k = 1 ; k < takes.size() ; k ++ ) {
				Take other = takes.get((i + k) % takes.size());
				if( !Arrays.equals(other.pitches, take.pitches) ) {
					Look control = look(take.audio, other.pitches, settings);
					wrong += control.accepted ? 1 : 0;
					differs += (control.chordSimulated || control.attackOnly) == control.accepted ? 0 : 1;
					report.append(line(take, other.pitches, control, false)).append('\n');
					if( control.accepted ) {
						causes.computeIfAbsent("wrong by " + (control.attackOnly ? "attack" : "chord check"), key -> new int[2])[1]++;
						sameNotesWrong += sameNotes(take.pitches, other.pitches) ? 1 : 0;
					} else {
						count(added, control);
						if( !sameNotes(take.pitches, other.pitches) ) {
							count(addedOther, control);
						}
					}
					break;
				}
			}
		}
		report.append(String.format("%n%d of %d accepted, %d wrongly accepted; simulation differing from the session: %d%n%n", accepted, takes.size(), wrong, differs));
		report.append(String.format("Wrong acceptances whose notes are those of the chord played (another voicing): %d of %d%n%n", sameNotesWrong, wrong));
		report.append("Causes: refused / wrongly accepted\n");
		for( Map.Entry<String, int[]> entry : causes.entrySet() ) {
			report.append(String.format("  %-24s %2d / %2d%n", entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
		}
		report.append("\nLevers (chord check simulated over the whole take, the strum window not applied: an upper bound): refusals recovered / wrong acceptances added (of them, another chord)\n");
		for( int l = 0 ; l < LEVERS.length ; l ++ ) {
			report.append(String.format("  %-34s %2d / %2d (%d)%n", LEVERS[l], recovered[l], added[l], addedOther[l]));
		}
		Path out = Paths.get("target", "dataset-benchmark", "owner-chords-diagnostic-" + engine + ".txt");
		Files.createDirectories(out.getParent());
		Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report);
	}

	private static final String[] LEVERS = { "L1 one string short", "L4 clipped judged", "L1+L4", "evidence 0.25", "evidence 0.20", "confirm 30 ms",
			"L1 without a clear partial", "two strings short", "confirmation with gaps", "L1 + confirmation with gaps" };

	private static void count(int[] counts, Look look) {
		boolean[] levers = { look.oneShort, look.clipped, look.oneShortClipped, look.evidence25, look.evidence20, look.confirm30, look.oneShortAny, look.twoShort,
				look.gapped, look.oneShortGapped };
		for( int l = 0 ; l < levers.length ; l ++ ) {
			counts[l] += levers[l] ? 1 : 0;
		}
	}
}
