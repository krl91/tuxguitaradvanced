package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
import app.tuxguitar.audio.engine.analysis.ChordEvidence;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * A probe for the plan of spec 024 (research, after the clarifications of 2026-11-05): what holding each partial at its highest over the last frames of a
 * strum (spec 023's experiment B, the hold in hops), with or without accepting a chord one string short (lever L1), would give on the owner's takes, each
 * judged against its own chord and against the chord of <b>every</b> other take. The chord check is simulated from the same evidence {@code ChordMatcher}
 * reads, computed on held spectra; the attack path is the real session's. A judgement counts as accepted when the attack path accepts or when the simulated
 * chord check is complete for the confirmation hops inside a strum window opened by an onset, as the session arms it. Each other chord is classed as clearly wrong
 * (two note names or more of the expected chord missing from the chord played), one name off, or another voicing (the same note names). The report goes to
 * {@code target/dataset-benchmark/owner-chords-strum-hold-<engine>.txt}. It asserts nothing about the figures.
 */
@Tag("dataset")
public class OwnerChordsStrumHoldProbeTest {

	private static final Path BASE = Paths.get("../../specs/004-attack-difference-recognition/benchmark");
	private static final int[] OPEN_STRINGS = { 64, 59, 55, 50, 45, 40 };
	private static final double LEAD_SECONDS = 0.5;
	private static final int[] HOLDS = { 1, 5, 10, 15, 20, 30 };
	private static final int[] CONFIRMS = { 8, 4 };

	private static final class Take {
		final String label;
		final int[] pitches;
		final RecordedAudio audio;

		Take(String label, int[] pitches, RecordedAudio audio) {
			this.label = label;
			this.pitches = pitches;
			this.audio = audio;
		}
	}

	private static List<Take> takes() throws IOException {
		List<Take> takes = new ArrayList<Take>();
		// the folders, as in OwnerChordsBenchmarkTest: -Dtuxguitar.owner.chords=dir1,dir2, else the first two series
		List<Path> folders = new ArrayList<Path>();
		String property = System.getProperty("tuxguitar.owner.chords");
		if( property != null && !property.isEmpty() ) {
			for( String folder : property.split(",") ) {
				folders.add(Paths.get(folder));
			}
		} else {
			folders.add(BASE.resolve("owner-chords"));
			folders.add(BASE.resolve("owner-chords-2"));
		}
		for( Path folder : folders ) {
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
					takes.add(new Take(folder.getFileName() + "/" + cells[0], pitches, audio));
				}
			}
		}
		return takes;
	}

	private static Set<Integer> names(int[] pitches) {
		Set<Integer> names = new TreeSet<Integer>();
		for( int pitch : pitches ) {
			names.add(pitch % 12);
		}
		return names;
	}

	/** 0: another voicing (same note names one way or the other), 1: one name of the expected chord missing, 2: clearly wrong (two or more missing). */
	static int distance(int[] played, int[] expected) {
		Set<Integer> p = names(played);
		Set<Integer> e = names(expected);
		if( p.containsAll(e) || e.containsAll(p) ) {
			return 0;
		}
		int missing = 0;
		for( int name : e ) {
			missing += p.contains(name) ? 0 : 1;
		}
		return missing >= 2 ? 2 : 1;
	}

	private static float[] samples(RecordedAudio audio) {
		float[] samples = new float[audio.samples.length + audio.sampleRate / 2];
		System.arraycopy(audio.samples, 0, samples, audio.sampleRate / 2, audio.samples.length);
		return samples;
	}

	private static PracticeSequence sequence(int[] expected) {
		int[] strings = DatasetJudge.strings(expected);
		SongFixture fixture = new SongFixture(1);
		for( int i = 0 ; i < expected.length ; i ++ ) {
			fixture.quarter(0, 0, strings[i], expected[i] - OPEN_STRINGS[strings[i] - 1]);
		}
		return fixture.sequence();
	}

	/** The real session with a chord check that can never be complete: only the attack path can accept. */
	private static boolean attackAccepts(RecordedAudio audio, int[] expected, RecognitionSettings settings) {
		int[] strings = DatasetJudge.strings(expected);
		SongFixture fixture = new SongFixture(1);
		for( int i = 0 ; i < expected.length ; i ++ ) {
			fixture.quarter(0, 0, strings[i], expected[i] - OPEN_STRINGS[strings[i] - 1]);
		}
		PracticeSequence sequence = fixture.sequence();
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeParameters parameters = PracticeParameters.of(settings);
		PracticeSession session = new PracticeSession(selection, sequence, parameters.noteInterpreter(), parameters.tolerance(), parameters.stabilityNanos(),
				parameters.articulationLatencyNanos(), new ChordMatcher(new ChordMatcher.Parameters(99, 1.0, 1, 60_000_000_000L)), null);
		session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		for( AnalysisFrame frame : OfflineAnalysis.analyze(samples(audio), audio.sampleRate, settings, expected.length > 1, AttackCandidates.of(sequence)) ) {
			if( session.onFrame(frame).getAcceptedCount() > 0 ) {
				return true;
			}
		}
		return false;
	}

	/** The spectra of the take with each peak held at its highest over the last {@code hold} frames (1: as analysed); null for a frame not judged. */
	private static List<SpectrumFrame> held(List<AnalysisFrame> frames, int hold) {
		List<SpectrumFrame> result = new ArrayList<SpectrumFrame>();
		List<float[]> recent = new ArrayList<float[]>();
		for( AnalysisFrame frame : frames ) {
			SpectrumFrame spectrum = frame.getSpectrum();
			boolean usable = frame.getLevelState() == AnalysisFrame.LevelState.SUITABLE || frame.getLevelState() == AnalysisFrame.LevelState.TOO_HIGH;
			if( spectrum == null ) {
				recent.clear();
				result.add(null);
				continue;
			}
			float[] peaks = new float[spectrum.getPeakLength()];
			for( int k = 0 ; k < peaks.length ; k ++ ) {
				peaks[k] = spectrum.getPeak(k);
			}
			recent.add(peaks);
			if( recent.size() > hold ) {
				recent.remove(0);
			}
			float[] max = new float[peaks.length];
			for( float[] r : recent ) {
				for( int k = 0 ; k < max.length && k < r.length ; k ++ ) {
					max[k] = Math.max(max[k], r[k]);
				}
			}
			result.add(usable ? SpectrumFrame.of(spectrum.getBinHz(), spectrum.getMinBandMidi(), new float[0], spectrum.getMinSalienceMidi(), new float[0],
					spectrum.getCandidates(), max) : null);
		}
		return result;
	}

	/** Whether the simulated chord check accepts: for each confirmation of {@link #CONFIRMS}, with all strings (index 0) or one string short (index 1). */
	private static boolean[][] chordAccepts(List<AnalysisFrame> frames, List<SpectrumFrame> spectra, int[] expected, ChordMatcher.Parameters chord,
			long latencyNanos) {
		int[] required = Arrays.stream(expected).distinct().sorted().toArray();
		boolean[][] accepted = new boolean[CONFIRMS.length][2];
		int[][] runs = new int[CONFIRMS.length][2];
		// the session's arming (PracticeSession.onFrame and onChordFrame): a strum window opens at an onset and lasts windowNanos; when it ends
		// without acceptance, the next onset opens another one
		boolean armed = false;
		long armReference = Long.MIN_VALUE;
		long windowStart = 0;
		for( int f = 0 ; f < frames.size() ; f ++ ) {
			AnalysisFrame frame = frames.get(f);
			long now = frame.getTimestampNanos();
			if( frame.isOnset() && now - latencyNanos > armReference && !armed ) {
				armed = true;
				windowStart = now - latencyNanos;
				for( int[] run : runs ) {
					Arrays.fill(run, 0);
				}
			}
			if( !armed ) {
				continue;
			}
			if( now - windowStart > chord.windowNanos ) {
				armed = false;
				armReference = now;
				continue;
			}
			SpectrumFrame spectrum = spectra.get(f);
			boolean complete = false;
			boolean oneShort = false;
			if( spectrum != null ) {
				ChordEvidence evidence = ChordEvidence.evaluate(spectrum, required, new int[0]);
				int verifiable = 0;
				int heard = 0;
				int missing = 0;
				int missingWithAClearPartial = 0;
				for( ChordEvidence.Pitch pitch : evidence.getPitches() ) {
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
				complete = heard > 0 && missing == 0;
				oneShort = complete || (verifiable >= 3 && missing == 1 && missingWithAClearPartial == 1);
			}
			for( int c = 0 ; c < CONFIRMS.length ; c ++ ) {
				runs[c][0] = complete ? runs[c][0] + 1 : 0;
				runs[c][1] = oneShort ? runs[c][1] + 1 : 0;
				for( int r = 0 ; r < 2 ; r ++ ) {
					accepted[c][r] |= runs[c][r] >= CONFIRMS[c];
				}
			}
		}
		return accepted;
	}

	@Test
	public void holdingEachStringOverTheStrum() throws Exception {
		List<Take> takes = takes();
		assumeTrue(!takes.isEmpty(), "the owner's recordings are not here");
		RecognitionSettings settings = RecognitionSettings.defaults();
		ChordMatcher.Parameters chord = PracticeParameters.of(settings).chordParameters();
		long latency = PracticeParameters.of(settings).articulationLatencyNanos();
		String engine = PitchEngineCatalog.resolveKind(settings).name().toLowerCase();
		// [hold][confirm][rule][class]: class 0 right chord, 1 another voicing, 2 one name off, 3 clearly wrong; plus the totals per class
		int[][][][] accepted = new int[HOLDS.length][CONFIRMS.length][2][4];
		int[] totals = new int[4];
		int[] attackAccepted = new int[4];
		StringBuilder detail = new StringBuilder();
		for( Take take : takes ) {
			List<AnalysisFrame> frames = OfflineAnalysis.analyze(samples(take.audio), take.audio.sampleRate, settings, true, new int[0]);
			List<List<SpectrumFrame>> spectra = new ArrayList<List<SpectrumFrame>>();
			for( int hold : HOLDS ) {
				spectra.add(held(frames, hold));
			}
			for( Take other : takes ) {
				int kind;
				if( other == take ) {
					kind = 0;
				} else if( Arrays.equals(other.pitches, take.pitches) ) {
					continue;
				} else {
					kind = 1 + distance(take.pitches, other.pitches);
				}
				int[] expected = (kind == 0 ? take.pitches : other.pitches);
				totals[kind]++;
				boolean attack = attackAccepts(take.audio, expected, settings);
				attackAccepted[kind] += attack ? 1 : 0;
				StringBuilder row = new StringBuilder();
				for( int h = 0 ; h < HOLDS.length ; h ++ ) {
					boolean[][] chordResult = chordAccepts(frames, spectra.get(h), expected, chord, latency);
					for( int c = 0 ; c < CONFIRMS.length ; c ++ ) {
						for( int r = 0 ; r < 2 ; r ++ ) {
							boolean yes = attack || chordResult[c][r];
							accepted[h][c][r][kind] += yes ? 1 : 0;
							row.append(yes ? '1' : '0');
						}
					}
					row.append(' ');
				}
				if( kind == 0 || kind == 3 ) {
					detail.append(String.format("%-26s expecting %-22s %-14s attack=%s %s%n", take.label, Arrays.toString(expected),
							kind == 0 ? "RIGHT" : "CLEARLY WRONG", attack ? 1 : 0, row));
				}
			}
		}
		StringBuilder report = new StringBuilder("Owner chords, strum hold probe (spec 024 plan), engine " + engine + "\n");
		report.append(String.format("Judgements: %d right chords, %d other voicings, %d one name off, %d clearly wrong (every other take's chord)%n", totals[0], totals[1],
				totals[2], totals[3]));
		report.append(String.format("Attack path alone accepts: right %d, voicings %d, one name off %d, clearly wrong %d%n%n", attackAccepted[0], attackAccepted[1],
				attackAccepted[2], attackAccepted[3]));
		report.append("hold(hops)  confirm  rule            right  voicing  one-off  clearly-wrong\n");
		for( int h = 0 ; h < HOLDS.length ; h ++ ) {
			for( int c = 0 ; c < CONFIRMS.length ; c ++ ) {
				for( int r = 0 ; r < 2 ; r ++ ) {
					int[] a = accepted[h][c][r];
					report.append(String.format("%5d       %4d     %-14s  %3d/%d  %3d/%d   %3d/%d   %3d/%d%n", HOLDS[h], CONFIRMS[c], r == 0 ? "all strings" : "one short",
							a[0], totals[0], a[1], totals[1], a[2], totals[2], a[3], totals[3]));
				}
			}
		}
		report.append("\nDetail (right and clearly wrong only); per hold, four digits: confirm 8 all, confirm 8 one short, confirm 4 all, confirm 4 one short\n");
		report.append(detail);
		Path out = Paths.get("target", "dataset-benchmark", "owner-chords-strum-hold-" + engine + ".txt");
		Files.createDirectories(out.getParent());
		Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report.substring(0, Math.min(report.length(), 4000)));
	}
}
