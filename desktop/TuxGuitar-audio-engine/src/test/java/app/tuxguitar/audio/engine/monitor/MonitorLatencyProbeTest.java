package app.tuxguitar.audio.engine.monitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The delay probe of spec 022 (research R1, task T002): how short a round trip Java Sound reaches on this computer.
 *
 * For each block size and output line buffer, a loop reads one block from the microphone and writes one block to the speakers, as the
 * monitor will. The block written is silence (the microphone is never played back, so there is no howl), except a 1 ms click every
 * 3 s. Each click is found in the captured stream; an input sample read in the same iteration as the click is
 * written at the same output position, so the distance between the click's input index and the first frame of that block is the
 * monitor's delay (input block, output buffer, both devices and about 1 ms of air). As the monitor's queue does, the loop never lets more
 * than one input burst ({@link #INPUT_BURST_FRAMES}) and one block wait in the input: the oldest are dropped (the capture reads on its own thread, so the
 * input never backs up there; only the queue to the output can).
 *
 * It only measures: it fails only if no line can be opened. Excluded by default (benchmark tag); run with
 * {@code -Dgroups=benchmark -DexcludedGroups= -Dtest=MonitorLatencyProbeTest}. The input is the built-in microphone, found by name
 * (override with {@code -Dprobe.input=<part of the mixer name>}); the output is the system's default.
 */
@Tag("benchmark")
public class MonitorLatencyProbeTest {

	private static final float RATE = 48000f;
	private static final int[] BLOCKS = { 64, 128, 256 };
	private static final int[] LINE_BLOCKS = { 2, 3, 4 };
	private static final int CLICKS = 20;
	private static final int CLICK_SPACING = 3 * 48000;
	private static final int CLICK_FRAMES = 48;
	private static final int RUN_FRAMES = 60 * 48000;
	/** Java Sound hands the built-in microphone's input over in bursts of 512 device frames (about 557 frames at 48 kHz); a burst is not a backlog. */
	private static final int INPUT_BURST_FRAMES = 600;

	@Test
	public void probe() throws Exception {
		AudioFormat inFormat = new AudioFormat(RATE, 16, 1, true, false);
		AudioFormat outFormat = new AudioFormat(RATE, 16, 2, true, false);
		Mixer.Info input = findInput(inFormat);
		StringBuilder report = new StringBuilder();
		report.append("# Delay probe (spec 022, research R1, task T002)\n\n");
		report.append("- Java ").append(System.getProperty("java.version")).append(", ").append(System.getProperty("os.name")).append(' ')
				.append(System.getProperty("os.version")).append(' ').append(System.getProperty("os.arch")).append('\n');
		report.append("- Input: ").append(input == null ? "the system's default input" : input.getName()).append(", 48 kHz, 16-bit mono\n");
		report.append("- Output: the system's default output, 48 kHz, 16-bit stereo\n");
		report.append("- The input line buffer is 4096 frames, as `CaptureService` opens it; only the read size changes.\n");
		report.append("- ").append(CLICKS).append(" clicks of 1 ms, 3 s apart, over 60 s per setting. The block written is silence apart from the clicks: the microphone is never played back.\n");
		report.append("- Delay = the click's index in the input minus the first frame of the input block read in the same iteration (that block would be written where the click was): input block + output buffer + devices + about 1 ms of air.\n");
		report.append("- As the monitor's queue must, no more than one block beyond one input burst of ").append(INPUT_BURST_FRAMES).append(" frames wait in the input (Java Sound hands the microphone over in bursts of 512 device frames, 11.6 ms): the oldest are dropped (Dropped column). Without it the delay grew by itself: the output is also drained in bursts of 512 device frames, so a line buffer smaller than that is not emptied at the input's pace.\n");
		report.append("- Underrun: the output line's `available()` equals its whole buffer before a write (nothing left to play). Short read: `read` returned less than a block.\n\n");
		report.append("| Block (frames) | Line buffer asked (blocks) | Line buffer got (frames) | Clicks found | Median (ms) | 95th percentile (ms) | Min (ms) | Max (ms) | Underruns | Short reads | Dropped blocks |\n");
		report.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
		List<String> failures = new ArrayList<String>();
		int opened = 0;
		for( int block : BLOCKS ) {
			for( int lineBlocks : LINE_BLOCKS ) {
				try {
					Result result = run(input, inFormat, outFormat, block, lineBlocks);
					opened++;
					report.append(result.row()).append('\n');
					System.out.println("probe " + result.row());
				} catch (Exception exception) {
					String reason = block + " x " + lineBlocks + ": " + exception;
					failures.add(reason);
					report.append("| ").append(block).append(" | ").append(lineBlocks).append(" | - | not run: ").append(exception.getMessage()).append(" | | | | | | |\n");
				}
			}
		}
		if( !failures.isEmpty() ) {
			report.append("\nSettings not run:\n");
			for( String failure : failures ) {
				report.append("- ").append(failure).append('\n');
			}
		}
		write(report.toString());
		Assertions.assertTrue(opened > 0, "no line could be opened: " + failures);
	}

	private static Result run(Mixer.Info inputInfo, AudioFormat inFormat, AudioFormat outFormat, int block, int lineBlocks) throws Exception {
		TargetDataLine in = inputInfo == null ? (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, inFormat))
				: (TargetDataLine) AudioSystem.getMixer(inputInfo).getLine(new DataLine.Info(TargetDataLine.class, inFormat));
		SourceDataLine out = AudioSystem.getSourceDataLine(outFormat);
		try {
			// the input line is opened as CaptureService opens it (1024 frames x 4), so the probe measures the capture as it is
			in.open(inFormat, 2 * 1024 * 4);
			out.open(outFormat, block * 4 * lineBlocks);
			int outBuffer = out.getBufferSize() / 4;
			byte[] inBytes = new byte[block * 2];
			byte[] outBytes = new byte[block * 4];
			byte[] silence = new byte[block * 4];
			short[] captured = new short[RUN_FRAMES + block];
			List<long[]> clicks = new ArrayList<long[]>();
			int underruns = 0;
			int shortReads = 0;
			int dropped = 0;
			byte[] discard = new byte[block * 2];
			// prime the output with all but one block of silence, then read one block and write one block
			int primeBlocks = Math.max(1, outBuffer / block - 1);
			for( int i = 0 ; i < primeBlocks ; i++ ) {
				out.write(silence, 0, silence.length);
			}
			out.start();
			in.start();
			long read = 0;
			int nextClick = CLICK_SPACING;
			boolean started = false;
			while( read < RUN_FRAMES ) {
				while( in.available() > 2 * INPUT_BURST_FRAMES + inBytes.length ) {
					int gone = readFully(in, discard) / 2;
					for( int i = 0 ; i < gone && read + i < captured.length ; i++ ) {
						captured[(int) read + i] = (short) ((discard[2 * i] & 0xff) | (discard[2 * i + 1] << 8));
					}
					read += gone;
					dropped++;
				}
				if( read >= RUN_FRAMES ) {
					break;
				}
				int got = readFully(in, inBytes);
				if( got < inBytes.length ) {
					shortReads++;
				}
				int frames = got / 2;
				for( int i = 0 ; i < frames ; i++ ) {
					captured[(int) read + i] = (short) ((inBytes[2 * i] & 0xff) | (inBytes[2 * i + 1] << 8));
				}
				long blockStart = read;
				read += frames;
				byte[] toWrite = silence;
				if( clicks.size() < CLICKS && read >= nextClick ) {
					fillClick(outBytes);
					toWrite = outBytes;
					clicks.add(new long[] { blockStart });
					nextClick += CLICK_SPACING;
				}
				if( started && out.available() >= out.getBufferSize() ) {
					underruns++;
				}
				started = true;
				out.write(toWrite, 0, toWrite.length);
			}
			double[] delays = findClicks(captured, (int) read, clicks);
			return new Result(block, lineBlocks, outBuffer, delays, underruns, shortReads, dropped);
		} finally {
			in.stop();
			in.close();
			out.stop();
			out.close();
		}
	}

	private static int readFully(TargetDataLine in, byte[] bytes) {
		int total = 0;
		while( total < bytes.length ) {
			int got = in.read(bytes, total, bytes.length - total);
			if( got <= 0 ) {
				break;
			}
			total += got;
		}
		return total;
	}

	private static void fillClick(byte[] bytes) {
		Arrays.fill(bytes, (byte) 0);
		for( int i = 0 ; i < CLICK_FRAMES ; i++ ) {
			double window = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (CLICK_FRAMES - 1));
			short value = (short) Math.round(0.8 * 32767 * window * Math.sin(2 * Math.PI * 3000 * i / RATE));
			for( int channel = 0 ; channel < 2 ; channel++ ) {
				bytes[4 * i + 2 * channel] = (byte) value;
				bytes[4 * i + 2 * channel + 1] = (byte) (value >> 8);
			}
		}
	}

	/** For each click, the first input sample after its block that rises well above the noise measured just before it. */
	private static double[] findClicks(short[] captured, int length, List<long[]> clicks) {
		List<Double> delays = new ArrayList<Double>();
		for( long[] click : clicks ) {
			int start = (int) click[0];
			int noiseFrom = Math.max(0, start - 4800);
			double sum = 0;
			for( int i = noiseFrom ; i < start ; i++ ) {
				sum += (double) captured[i] * captured[i];
			}
			double rms = start > noiseFrom ? Math.sqrt(sum / (start - noiseFrom)) : 0;
			double threshold = Math.max(600, 8 * rms);
			int end = Math.min(length, start + 14400);
			for( int i = start ; i < end ; i++ ) {
				if( Math.abs(captured[i]) > threshold ) {
					delays.add((i - start) * 1000.0 / RATE);
					break;
				}
			}
		}
		double[] result = new double[delays.size()];
		for( int i = 0 ; i < result.length ; i++ ) {
			result[i] = delays.get(i);
		}
		return result;
	}

	private static Mixer.Info findInput(AudioFormat format) {
		String wanted = System.getProperty("probe.input", "");
		Line.Info target = new DataLine.Info(TargetDataLine.class, format);
		// the computer's own microphone, never a phone's (Continuity) or a call application's
		Mixer.Info builtIn = null;
		for( Mixer.Info info : AudioSystem.getMixerInfo() ) {
			Mixer mixer = AudioSystem.getMixer(info);
			if( !mixer.isLineSupported(target) ) {
				continue;
			}
			String name = info.getName().toLowerCase(Locale.ROOT);
			System.out.println("probe input candidate: " + info.getName());
			if( !wanted.isEmpty() ) {
				if( name.contains(wanted.toLowerCase(Locale.ROOT)) ) {
					return info;
				}
			} else if( builtIn == null && (name.contains("macbook") || name.contains("built-in") || name.contains("imac") || name.contains("mac mini")) ) {
				builtIn = info;
			}
		}
		return builtIn;
	}

	private static void write(String report) throws IOException {
		Path repository = null;
		for( Path path = Paths.get(System.getProperty("user.dir")).toAbsolutePath() ; path != null ; path = path.getParent() ) {
			if( Files.isDirectory(path.resolve("specs/022-guitar-amp-effects")) ) {
				repository = path;
				break;
			}
		}
		Assertions.assertNotNull(repository, "repository root not found from " + System.getProperty("user.dir"));
		Path output = repository.resolve("specs/022-guitar-amp-effects/benchmark/latency-probe.md");
		Files.createDirectories(output.getParent());
		Files.write(output, report.getBytes(StandardCharsets.UTF_8));
		System.out.println("probe report: " + output);
	}

	private static final class Result {
		private final int block;
		private final int lineBlocks;
		private final int outBuffer;
		private final double[] delays;
		private final int underruns;
		private final int shortReads;
		private final int dropped;

		private Result(int block, int lineBlocks, int outBuffer, double[] delays, int underruns, int shortReads, int dropped) {
			this.block = block;
			this.lineBlocks = lineBlocks;
			this.outBuffer = outBuffer;
			this.delays = delays.clone();
			Arrays.sort(this.delays);
			this.underruns = underruns;
			this.shortReads = shortReads;
			this.dropped = dropped;
		}

		private String row() {
			StringBuilder row = new StringBuilder();
			row.append("| ").append(this.block).append(" | ").append(this.lineBlocks).append(" | ").append(this.outBuffer).append(" | ")
					.append(this.delays.length).append(" / ").append(CLICKS).append(" | ");
			if( this.delays.length == 0 ) {
				row.append("- | - | - | - | ");
			} else {
				row.append(ms(percentile(0.5))).append(" | ").append(ms(percentile(0.95))).append(" | ").append(ms(this.delays[0])).append(" | ")
						.append(ms(this.delays[this.delays.length - 1])).append(" | ");
			}
			row.append(this.underruns).append(" | ").append(this.shortReads).append(" | ").append(this.dropped).append(" |");
			return row.toString();
		}

		private double percentile(double p) {
			int index = (int) Math.ceil(p * this.delays.length) - 1;
			return this.delays[Math.max(0, Math.min(this.delays.length - 1, index))];
		}

		private static String ms(double value) {
			return String.format(Locale.ROOT, "%.1f", value);
		}
	}
}
