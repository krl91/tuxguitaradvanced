package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * The probe of spec 024 (tasks T006, research R3 and R6): the chord's pitch-class profile computed from the sessions' own spectra, with the engine of
 * {@code -Dtuxguitar.recognition.engine}, on the owner's series of {@code -Dtuxguitar.owner.chords} (default: the first two series). The profile sums, per
 * note name, the linear (or square-root) magnitude of the semitone bands from one semitone under the expected chord's lowest pitch up to {@code top}, the bands
 * more than {@code noise} dB under the frame's strongest summed band left out; it is matched by cosine against the expected names. A frame is complete when the
 * match reaches the threshold, at most one expected name is weak (under {@code share} of the strongest expected name's sum) and {@code settle} has passed;
 * the chord is accepted after {@code confirm} complete frames in a row, within the strum window. Two arming references: Wait's (an onset arms one window at a
 * time, the profile summed from the arming frame) and Follow's (every onset opens its own window from its transient, the profile summed from the transient).
 * The attack path is the real session's; when kept, it is guarded: it accepts only if the profile at its frame has at most one weak name.
 * <p>
 * Each take of the reference series ({@code owner-chords-3}) is judged against its own chord and against every other take's chord; each take of another series
 * against its own chord only (a guard, at least {@code accepted.min} of {@code owner-chords-baseline.properties}). The frames are analysed once per take; for
 * each judgement and arming the match is recorded per frame, so every threshold is read from one pass. The leak is the highest share (of the strongest
 * expected name's sum) that a name not played reaches in the reference takes judged against their own chord, at the end of each window.
 * <p>
 * Reports: {@code target/dataset-benchmark/owner-chords-profile-probe-<engine>.txt} (summary) and {@code .tsv} (every configuration and threshold). It
 * asserts nothing about the figures.
 */
@Tag("dataset")
public class OwnerChordsProfileProbeTest {

	private static final Path BASE = Paths.get("../../specs/004-attack-difference-recognition/benchmark");
	private static final String REFERENCE = "owner-chords-3";
	private static final int[] OPEN_STRINGS = { 64, 59, 55, 50, 45, 40 };

	private static final int THRESHOLD_FROM = 70;
	private static final int THRESHOLD_TO = 95;
	private static final long[] SETTLES_MS = { 0, 50, 100, 200 };
	private static final double[] SHARES = { 0.05, 0.10, 0.20, 0.30, 0.40, 0.50 };
	private static final int[] TOPS = { 76, 84, 88, 96 };
	private static final double[] NOISES = { 0, 40, 30 };
	private static final String[] WEIGHTS = { "linear", "root" };
	private static final int[] CONFIRMS = { 8, 4 };
	private static final String[] ATTACKS = { "off", "guarded" };
	private static final String[] REFS = { "wait", "follow" };
	private static final double LEAK_MARGIN = 0.05;

	/** right, voicing, one name off, clearly wrong */
	private static final int CLASSES = 4;

	private static final class Take {
		final String folder;
		final String label;
		final int[] pitches;
		final RecordedAudio audio;
		long[] times;
		boolean[] usable;
		boolean[] onset;
		int minBand;
		double[][] bands;
		List<int[]> waitAttempts;
		List<int[]> followAttempts;

		Take(String folder, String label, int[] pitches, RecordedAudio audio) {
			this.folder = folder;
			this.label = label;
			this.pitches = pitches;
			this.audio = audio;
		}
	}

	private static List<Take> takes() throws IOException {
		List<Take> takes = new ArrayList<Take>();
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
					takes.add(new Take(folder.getFileName().toString(), cells[0], pitches, audio));
				}
			}
		}
		return takes;
	}

	private static float[] samples(RecordedAudio audio) {
		float[] samples = new float[audio.samples.length + audio.sampleRate / 2];
		System.arraycopy(audio.samples, 0, samples, audio.sampleRate / 2, audio.samples.length);
		return samples;
	}

	/** The time of the frame where the real session's attack path accepts, with a chord check that can never be complete; Long.MIN_VALUE if never. */
	private static long attackTime(RecordedAudio audio, int[] expected, RecognitionSettings settings) {
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
				return frame.getTimestampNanos();
			}
		}
		return Long.MIN_VALUE;
	}

	/** The frames of a take, analysed once: times, usable levels, onsets and the semitone band levels. */
	private static void analyse(Take take, RecognitionSettings settings, long latency, long window) {
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(samples(take.audio), take.audio.sampleRate, settings, true, new int[0]);
		int n = frames.size();
		take.times = new long[n];
		take.usable = new boolean[n];
		take.onset = new boolean[n];
		take.bands = new double[n][];
		take.minBand = Integer.MAX_VALUE;
		for( int f = 0 ; f < n ; f ++ ) {
			AnalysisFrame frame = frames.get(f);
			SpectrumFrame spectrum = frame.getSpectrum();
			take.times[f] = frame.getTimestampNanos();
			take.onset[f] = frame.isOnset();
			take.usable[f] = spectrum != null && (frame.getLevelState() == AnalysisFrame.LevelState.SUITABLE || frame.getLevelState() == AnalysisFrame.LevelState.TOO_HIGH);
			if( spectrum != null ) {
				take.minBand = Math.min(take.minBand, spectrum.getMinBandMidi());
				double[] db = new double[spectrum.getBandCount()];
				for( int b = 0 ; b < db.length ; b ++ ) {
					db[b] = spectrum.getBandDb(spectrum.getMinBandMidi() + b);
				}
				take.bands[f] = db;
			}
		}
		// Wait (PracticeSession.onFrame and onChordFrame): an onset arms one window at a time; the window ends on the first usable frame past it, that frame
		// judged first; the next onset after it arms again. Each attempt: { first frame summed, last frame judged, reference frame index }
		take.waitAttempts = new ArrayList<int[]>();
		boolean armed = false;
		long armReference = Long.MIN_VALUE;
		long windowStart = 0;
		int from = 0;
		for( int f = 0 ; f < n ; f ++ ) {
			long now = take.times[f];
			if( !armed && take.onset[f] && now - latency > armReference ) {
				armed = true;
				windowStart = now - latency;
				from = f;
			}
			if( armed && take.usable[f] && now - windowStart > window ) {
				take.waitAttempts.add(new int[] { from, f, from });
				armed = false;
				armReference = now;
			}
		}
		if( armed ) {
			take.waitAttempts.add(new int[] { from, n - 1, from });
		}
		// Follow (FollowSession.registerOnset and evaluateFrame): every onset opens its window at its transient (now - latency), the frames since the
		// transient judged; it ends on the first usable frame whose age passes the window
		take.followAttempts = new ArrayList<int[]>();
		for( int f = 0 ; f < n ; f ++ ) {
			if( !take.onset[f] ) {
				continue;
			}
			long transient_ = take.times[f] - latency;
			int start = f;
			while( start > 0 && take.times[start - 1] >= transient_ ) {
				start --;
			}
			int end = n - 1;
			for( int g = start ; g < n ; g ++ ) {
				if( take.usable[g] && take.times[g] - transient_ > window ) {
					end = g;
					break;
				}
			}
			take.followAttempts.add(new int[] { start, end, start });
		}
	}

	/** The profile along one attempt: per frame the match, the second lowest share of an expected name (weak names ≤ 1 iff it reaches the share), the leak. */
	private static final class Trace {
		final double[] match;
		final double[] second;
		final long[] summed;
		final boolean[] usable;
		double leakAtEnd;

		Trace(int length) {
			this.match = new double[length];
			this.second = new double[length];
			this.summed = new long[length];
			this.usable = new boolean[length];
		}
	}

	private static Trace trace(Take take, int[] attempt, int[] expected, int top, double noise, boolean root) {
		boolean[] template = new boolean[12];
		int low = Integer.MAX_VALUE;
		for( int pitch : expected ) {
			template[pitch % 12] = true;
			low = Math.min(low, pitch);
		}
		low = Math.max(low - 1, take.minBand);
		int names = 0;
		for( boolean t : template ) {
			names += t ? 1 : 0;
		}
		double[] sums = new double[12];
		int length = attempt[1] - attempt[0] + 1;
		Trace trace = new Trace(length);
		double lastMatch = 0;
		double lastSecond = 0;
		long reference = take.times[attempt[2]];
		for( int i = 0 ; i < length ; i ++ ) {
			int f = attempt[0] + i;
			trace.usable[i] = take.usable[f];
			trace.summed[i] = take.times[f] - reference;
			if( take.usable[f] && take.bands[f] != null ) {
				double[] db = take.bands[f];
				int first = Math.max(0, low - take.minBand);
				int last = Math.min(db.length - 1, top - take.minBand);
				double strongest = Double.NEGATIVE_INFINITY;
				for( int b = first ; b <= last ; b ++ ) {
					strongest = Math.max(strongest, db[b]);
				}
				for( int b = first ; b <= last ; b ++ ) {
					if( db[b] == Double.NEGATIVE_INFINITY || (noise > 0 && db[b] < strongest - noise) ) {
						continue;
					}
					double magnitude = Math.pow(10, db[b] / 20.0);
					sums[(take.minBand + b) % 12] += root ? Math.sqrt(magnitude) : magnitude;
				}
				double dot = 0;
				double norm = 0;
				double maxExpected = 0;
				for( int k = 0 ; k < 12 ; k ++ ) {
					norm += sums[k] * sums[k];
					if( template[k] ) {
						dot += sums[k];
						maxExpected = Math.max(maxExpected, sums[k]);
					}
				}
				lastMatch = (norm > 0 ? dot / (Math.sqrt(norm) * Math.sqrt(names)) : 0);
				double lowest = Double.MAX_VALUE;
				double secondLowest = Double.MAX_VALUE;
				double leak = 0;
				for( int k = 0 ; k < 12 ; k ++ ) {
					double share = (maxExpected > 0 ? sums[k] / maxExpected : 0);
					if( template[k] ) {
						if( share < lowest ) {
							secondLowest = lowest;
							lowest = share;
						} else if( share < secondLowest ) {
							secondLowest = share;
						}
					} else {
						leak = Math.max(leak, share);
					}
				}
				lastSecond = (names < 2 ? Double.MAX_VALUE : secondLowest);
				trace.leakAtEnd = leak;
			}
			trace.match[i] = lastMatch;
			trace.second[i] = lastSecond;
		}
		return trace;
	}

	/** The highest threshold at which the attempt accepts (confirm complete frames in a row), or -1 if none; +inf if the guarded attack path accepts. */
	private static double acceptance(Trace trace, long settleNanos, double share, int confirm, int attackIndex) {
		if( attackIndex >= 0 && attackIndex < trace.match.length && trace.second[attackIndex] >= share ) {
			return Double.POSITIVE_INFINITY;
		}
		double best = -1;
		int run = 0;
		double[] window = new double[confirm];
		for( int i = 0 ; i < trace.match.length ; i ++ ) {
			if( !trace.usable[i] ) {
				run = 0;
				continue;
			}
			boolean candidate = trace.summed[i] >= settleNanos && trace.second[i] >= share;
			if( !candidate ) {
				run = 0;
				continue;
			}
			window[run % confirm] = trace.match[i];
			run ++;
			if( run >= confirm ) {
				double min = Double.MAX_VALUE;
				for( double v : window ) {
					min = Math.min(min, v);
				}
				best = Math.max(best, min);
			}
		}
		return best;
	}

	private static int distanceClass(Take played, Take expected) {
		if( played == expected ) {
			return 0;
		}
		return 1 + OwnerChordsStrumHoldProbeTest.distance(played.pitches, expected.pitches);
	}

	@Test
	public void pitchClassProfileSweep() throws Exception {
		List<Take> takes = takes();
		assumeTrue(!takes.isEmpty(), "the owner's recordings are not here");
		RecognitionSettings settings = RecognitionSettings.defaults();
		PracticeParameters parameters = PracticeParameters.of(settings);
		long latency = parameters.articulationLatencyNanos();
		long window = parameters.chordParameters().windowNanos;
		String engine = PitchEngineCatalog.resolveKind(settings).name().toLowerCase();
		Properties baseline = new Properties();
		try( InputStream in = Files.newInputStream(Paths.get("test/app/tuxguitar/app/tools/custom/practice/dataset/owner-chords-baseline.properties")) ) {
			baseline.load(in);
		}
		Map<String, Integer> folders = new LinkedHashMap<String, Integer>();
		for( Take take : takes ) {
			if( !folders.containsKey(take.folder) ) {
				folders.put(take.folder, folders.size());
			}
			analyse(take, settings, latency, window);
		}
		int variants = TOPS.length * NOISES.length * WEIGHTS.length;
		int decisions = SETTLES_MS.length * SHARES.length * CONFIRMS.length * ATTACKS.length;
		int thresholds = THRESHOLD_TO - THRESHOLD_FROM + 1;
		// [ref][variant][decision][threshold][folder][class]
		int[][][][][][] accepted = new int[REFS.length][variants][decisions][thresholds][folders.size()][CLASSES];
		int[][] totals = new int[folders.size()][CLASSES];
		// the margin: the weakest right acceptance threshold, the strongest clearly wrong one, per [ref][variant][decision]
		double[][][] weakestRight = new double[REFS.length][variants][decisions];
		double[][][] strongestWrong = new double[REFS.length][variants][decisions];
		for( double[][] a : weakestRight ) {
			for( double[] b : a ) {
				Arrays.fill(b, Double.POSITIVE_INFINITY);
			}
		}
		for( double[][] a : strongestWrong ) {
			for( double[] b : a ) {
				Arrays.fill(b, -1);
			}
		}
		double[][] leak = new double[REFS.length][variants];
		int done = 0;
		StringBuilder detail = new StringBuilder();
		for( Take take : takes ) {
			for( Take other : takes ) {
				boolean reference = REFERENCE.equals(take.folder);
				if( other != take && (!reference || !other.folder.equals(take.folder) || Arrays.equals(other.pitches, take.pitches)) ) {
					continue;
				}
				int kind = distanceClass(take, other);
				int folder = folders.get(take.folder);
				totals[folder][kind]++;
				int[] expected = other.pitches;
				long attack = attackTime(take.audio, expected, settings);
				if( kind == 0 ) {
					// the detail of each right chord, with top 88, no noise floor, linear weight: attempts, the best match and the second lowest share
					for( int r = 0 ; r < REFS.length ; r ++ ) {
						List<int[]> attempts = (r == 0 ? take.waitAttempts : take.followAttempts);
						double bestMatch = 0;
						double bestSecond = 0;
						int usable = 0;
						for( int[] attempt : attempts ) {
							Trace trace = trace(take, attempt, expected, 88, 0, false);
							for( int i = 0 ; i < trace.match.length ; i ++ ) {
								usable += trace.usable[i] ? 1 : 0;
								bestMatch = Math.max(bestMatch, trace.match[i]);
								bestSecond = Math.max(bestSecond, trace.second[i] == Double.MAX_VALUE ? 1 : trace.second[i]);
							}
						}
						detail.append(String.format("%-26s %-6s %-22s attempts %d, usable frames %d, best match %.3f, best second share %.3f, attack %s%n",
								take.folder + "/" + take.label, REFS[r], Arrays.toString(expected), attempts.size(), usable, bestMatch, bestSecond,
								attack == Long.MIN_VALUE ? "no" : "yes"));
					}
				}
				for( int r = 0 ; r < REFS.length ; r ++ ) {
					List<int[]> attempts = (r == 0 ? take.waitAttempts : take.followAttempts);
					int v = 0;
					for( int top : TOPS ) {
						for( double noise : NOISES ) {
							for( String weight : WEIGHTS ) {
								double[] best = new double[decisions];
								Arrays.fill(best, -1);
								// the attack path may accept with no strum armed (no onset detected): its guard then reads the profile of its own frame
								List<int[]> judged = new ArrayList<int[]>(attempts);
								int attackFrame = -1;
								for( int g = 0 ; attack != Long.MIN_VALUE && g < take.times.length ; g ++ ) {
									if( take.times[g] >= attack ) {
										attackFrame = g;
										break;
									}
								}
								boolean contained = false;
								for( int[] attempt : attempts ) {
									contained |= attackFrame >= attempt[0] && attackFrame <= attempt[1];
								}
								if( attackFrame >= 0 && !contained ) {
									judged.add(new int[] { attackFrame, attackFrame, attackFrame });
								}
								for( int[] attempt : judged ) {
									Trace trace = trace(take, attempt, expected, top, noise, "root".equals(weight));
									if( kind == 0 && reference && attempt[1] > attempt[0] ) {
										leak[r][v] = Math.max(leak[r][v], trace.leakAtEnd);
									}
									int attackIndex = -1;
									if( attackFrame >= attempt[0] && attackFrame <= attempt[1] ) {
										attackIndex = attackFrame - attempt[0];
									}
									int d = 0;
									for( long settle : SETTLES_MS ) {
										for( double share : SHARES ) {
											for( int confirm : CONFIRMS ) {
												for( String a : ATTACKS ) {
													double t = acceptance(trace, settle * 1_000_000L, share, confirm, "guarded".equals(a) ? attackIndex : -1);
													best[d] = Math.max(best[d], t);
													d ++;
												}
											}
										}
									}
								}
								for( int d = 0 ; d < decisions ; d ++ ) {
									for( int t = 0 ; t < thresholds ; t ++ ) {
										if( best[d] >= (THRESHOLD_FROM + t) / 100.0 ) {
											accepted[r][v][d][t][folder][kind]++;
										}
									}
									if( reference && kind == 0 ) {
										weakestRight[r][v][d] = Math.min(weakestRight[r][v][d], best[d]);
									} else if( reference && kind == 3 ) {
										strongestWrong[r][v][d] = Math.max(strongestWrong[r][v][d], best[d]);
									}
								}
								v ++;
							}
						}
					}
				}
				if( ++done % 100 == 0 ) {
					System.out.println("PROFILE PROBE " + engine + ": " + done + " judgements");
				}
			}
		}
		int ref = folders.containsKey(REFERENCE) ? folders.get(REFERENCE) : -1;
		StringBuilder tsv = new StringBuilder("ref\ttop\tnoise\tweight\tsettle_ms\tshare\tconfirm\tattack\tthreshold\tleak");
		for( String name : folders.keySet() ) {
			tsv.append('\t').append(name).append(".right\t").append(name).append(".voicing\t").append(name).append(".oneoff\t").append(name).append(".clearlywrong");
		}
		tsv.append("\tweakest_right\tstrongest_clearly_wrong\n");
		// qualifying: both references, the reference series all right and none clearly wrong, the share above the leak with the margin, the guards' minimum
		List<String> qualifying = new ArrayList<String>();
		List<double[]> ranking = new ArrayList<double[]>();
		for( int v = 0 ; v < variants ; v ++ ) {
			int top = TOPS[v / (NOISES.length * WEIGHTS.length)];
			double noise = NOISES[(v / WEIGHTS.length) % NOISES.length];
			String weight = WEIGHTS[v % WEIGHTS.length];
			for( int d = 0 ; d < decisions ; d ++ ) {
				long settle = SETTLES_MS[d / (SHARES.length * CONFIRMS.length * ATTACKS.length)];
				double share = SHARES[(d / (CONFIRMS.length * ATTACKS.length)) % SHARES.length];
				int confirm = CONFIRMS[(d / ATTACKS.length) % CONFIRMS.length];
				String attack = ATTACKS[d % ATTACKS.length];
				for( int t = 0 ; t < thresholds ; t ++ ) {
					boolean both = true;
					int worstOneOff = 0;
					int worstRight = Integer.MAX_VALUE;
					int worstWrong = 0;
					for( int r = 0 ; r < REFS.length ; r ++ ) {
						int[][] counts = accepted[r][v][d][t];
						tsv.append(REFS[r]).append('\t').append(top).append('\t').append(noise == 0 ? "none" : String.valueOf((int) noise)).append('\t').append(weight)
								.append('\t').append(settle).append('\t').append(share).append('\t').append(confirm).append('\t').append(attack).append('\t')
								.append(String.format("%.2f", (THRESHOLD_FROM + t) / 100.0)).append('\t').append(String.format("%.3f", leak[r][v]));
						for( int[] c : counts ) {
							for( int k = 0 ; k < CLASSES ; k ++ ) {
								tsv.append('\t').append(c[k]);
							}
						}
						tsv.append('\t').append(String.format("%.3f", weakestRight[r][v][d])).append('\t').append(String.format("%.3f", strongestWrong[r][v][d])).append('\n');
						boolean ok = ref >= 0 && counts[ref][0] == totals[ref][0] && counts[ref][3] == 0 && share >= leak[r][v] + LEAK_MARGIN;
						for( Map.Entry<String, Integer> f : folders.entrySet() ) {
							String min = baseline.getProperty(f.getKey() + ".accepted.min");
							if( min != null && counts[f.getValue()][0] < Integer.parseInt(min) ) {
								ok = false;
							}
						}
						both &= ok;
						if( ref >= 0 ) {
							worstOneOff = Math.max(worstOneOff, counts[ref][2]);
							worstRight = Math.min(worstRight, counts[ref][0]);
							worstWrong = Math.max(worstWrong, counts[ref][3]);
						}
					}
					String row = String.format("top %d, noise %s, %s, settle %d ms, share %.2f, confirm %d, attack %s, threshold %.2f", top,
							noise == 0 ? "none" : (int) noise + " dB", weight, settle, share, confirm, attack, (THRESHOLD_FROM + t) / 100.0);
					if( both ) {
						qualifying.add(String.format("%-110s one-off (worst ref) %3d, leak %.3f/%.3f, margin wait %.3f>%.3f follow %.3f>%.3f", row, worstOneOff,
								leak[0][v], leak[1][v], weakestRight[0][v][d], strongestWrong[0][v][d], weakestRight[1][v][d], strongestWrong[1][v][d]));
						ranking.add(new double[] { worstOneOff, -(THRESHOLD_FROM + t), settle, qualifying.size() - 1 });
					}
					if( ref >= 0 ) {
						ranking.add(new double[] { 1_000_000 - worstRight * 1000 + worstWrong, -(THRESHOLD_FROM + t), settle, -1 - qualifying.size(),
								v, d, t, worstRight, worstWrong, worstOneOff });
					}
				}
			}
		}
		StringBuilder report = new StringBuilder("Owner chords, pitch-class profile probe (spec 024, T006), engine " + engine + "\n");
		for( Map.Entry<String, Integer> f : folders.entrySet() ) {
			int[] c = totals[f.getValue()];
			report.append(String.format("%s: %d right, %d voicings, %d one name off, %d clearly wrong judgements%n", f.getKey(), c[0], c[1], c[2], c[3]));
		}
		report.append(String.format("Leak margin %.2f; guards from owner-chords-baseline.properties (accepted.min)%n%n", LEAK_MARGIN));
		report.append("Qualifying configurations (both arming references), the Normal rule's order: fewest one name off, then highest threshold, then shortest settle: "
				+ qualifying.size() + "\n");
		List<double[]> ordered = new ArrayList<double[]>();
		for( double[] rank : ranking ) {
			if( rank[3] >= 0 ) {
				ordered.add(rank);
			}
		}
		ordered.sort((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : a[1] != b[1] ? Double.compare(a[1], b[1]) : Double.compare(a[2], b[2]));
		for( int i = 0 ; i < Math.min(60, ordered.size()) ; i ++ ) {
			report.append(qualifying.get((int) ordered.get(i)[3])).append('\n');
		}
		if( qualifying.isEmpty() ) {
			report.append("\nNone qualifies. The best by right chords (worst reference), then clearly wrong:\n");
			List<double[]> best = new ArrayList<double[]>();
			for( double[] rank : ranking ) {
				if( rank[3] < 0 ) {
					best.add(rank);
				}
			}
			best.sort((a, b) -> Double.compare(a[0], b[0]));
			for( int i = 0 ; i < Math.min(40, best.size()) ; i ++ ) {
				double[] b = best.get(i);
				int v = (int) b[4];
				int d = (int) b[5];
				int t = (int) b[6];
				report.append(String.format("top %d, noise %s, %s, settle %d ms, share %.2f, confirm %d, attack %s, threshold %.2f: right %d, clearly wrong %d, one-off %d, leak %.3f/%.3f%n",
						TOPS[v / (NOISES.length * WEIGHTS.length)], NOISES[(v / WEIGHTS.length) % NOISES.length] == 0 ? "none" : (int) NOISES[(v / WEIGHTS.length) % NOISES.length] + " dB",
						WEIGHTS[v % WEIGHTS.length], SETTLES_MS[d / (SHARES.length * CONFIRMS.length * ATTACKS.length)],
						SHARES[(d / (CONFIRMS.length * ATTACKS.length)) % SHARES.length], CONFIRMS[(d / ATTACKS.length) % CONFIRMS.length], ATTACKS[d % ATTACKS.length],
						(THRESHOLD_FROM + t) / 100.0, (int) b[7], (int) b[8], (int) b[9], leak[0][v], leak[1][v]));
			}
		}
		report.append("\nRight chords (top 88, no noise floor, linear):\n").append(detail);
		Path out = Paths.get("target", "dataset-benchmark", "owner-chords-profile-probe-" + engine + ".txt");
		Files.createDirectories(out.getParent());
		Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
		Files.write(Paths.get("target", "dataset-benchmark", "owner-chords-profile-probe-" + engine + ".tsv"), tsv.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report.substring(0, Math.min(report.length(), 6000)));
	}
}
