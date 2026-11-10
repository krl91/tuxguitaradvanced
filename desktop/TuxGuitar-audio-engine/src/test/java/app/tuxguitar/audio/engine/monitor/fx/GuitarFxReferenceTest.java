package app.tuxguitar.audio.engine.monitor.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.benchmark.MonitorFixtureSet;
import app.tuxguitar.audio.engine.monitor.AmpSettings;
import app.tuxguitar.audio.engine.monitor.ChainSettings;
import app.tuxguitar.audio.engine.monitor.EffectKind;
import app.tuxguitar.audio.engine.monitor.GuitarFxReferenceRuns;
import app.tuxguitar.audio.engine.monitor.Knob;
import app.tuxguitar.audio.engine.monitor.MonitorEngine;
import app.tuxguitar.audio.engine.monitor.Pedal;
import app.tuxguitar.audio.engine.monitor.PedalSettings;
import app.tuxguitar.audio.engine.monitor.ReadyMadeChains;

/**
 * Writes the Java references of GuitarFX (spec 027, T005, SC-003): every element of the amp sound of spec 022, the whole chain of each
 * ready-made sound, the feedback guard and the output stage, run on the same signals at 44.1, 48 and 96 kHz, with the default knobs, each
 * knob at its minimum and at its maximum, and a change of every knob in the middle. The Rust port reads them (`guitarfx-dsp/tests/
 * java_reference.rs`, with {@code GUITARFX_REFERENCE_DIR}) and runs the same element on the same input and knobs.
 *
 * <p>Each run is in blocks of {@link MonitorEngine#BLOCK_FRAMES} frames. The files are little-endian {@code f32}: the inputs in
 * {@code inputs/}, the outputs in {@code outputs/}; {@code index.properties} lists every run (its element, variant, rate, signal, knobs,
 * input and output) and the four ready-made chain texts.
 */
public class GuitarFxReferenceTest {

	private static final Path FOLDER = Paths.get("target", "guitarfx-reference");
	private static final float[] RATES = { 44100f, 48000f, 96000f };
	private static final int FRAMES = 8192;
	private static final int RECORDING_FRAMES = 16384;
	private static final int BLOCK = MonitorEngine.BLOCK_FRAMES;

	private final Map<String, String> index = new TreeMap<String, String>();
	private int runs;

	/** The signals, the same at every rate (the samples are written as they are). */
	private static Map<String, float[]> signals() throws Exception {
		Map<String, float[]> signals = new LinkedHashMap<String, float[]>();
		signals.put("sine-quiet", Signals.sine(220, 0.01, FRAMES));
		signals.put("sine-medium", Signals.sine(220, 0.1, FRAMES));
		signals.put("sine-loud", Signals.sine(220, 0.5, FRAMES));
		signals.put("noise", Signals.noise(-40, FRAMES, 27));
		signals.put("silence", new float[FRAMES]);
		float[] step = new float[FRAMES];
		for( int i = FRAMES / 2 ; i < FRAMES ; i++ ) {
			step[i] = 0.5f;
		}
		signals.put("step", step);
		List<MonitorFixtureSet.Input> inputs = MonitorFixtureSet.recognitionInputs();
		assertTrue(!inputs.isEmpty(), "the recorded guitars of spec 022");
		float[] recording = inputs.get(0).samples();
		float[] cut = new float[Math.min(RECORDING_FRAMES, recording.length)];
		System.arraycopy(recording, 0, cut, 0, cut.length);
		signals.put("recording", cut);
		return signals;
	}

	@Test
	public void writesTheReferences() throws Exception {
		Files.createDirectories(FOLDER.resolve("inputs"));
		Files.createDirectories(FOLDER.resolve("outputs"));
		Map<String, float[]> signals = signals();
		for( float rate : RATES ) {
			for( Map.Entry<String, float[]> signal : signals.entrySet() ) {
				String input = "inputs/" + signal.getKey() + "-" + (int) rate + ".f32";
				write(FOLDER.resolve(input), signal.getValue());
				pedals(rate, signal.getKey(), input, signal.getValue());
				amp(rate, signal.getKey(), input, signal.getValue());
				cabinet(rate, signal.getKey(), input, signal.getValue());
				filters(rate, signal.getKey(), input, signal.getValue());
				oversamplers(rate, signal.getKey(), input, signal.getValue());
				outputStage(rate, signal.getKey(), input, signal.getValue());
				chains(rate, signal.getKey(), input, signal.getValue());
				guard(rate, signal.getKey(), input, signal.getValue());
			}
			// a howl the guard must find: a 1 kHz tone that grows from -40 to -6 dBFS over one second
			float[] howl = howl(rate);
			String input = "inputs/howl-" + (int) rate + ".f32";
			write(FOLDER.resolve(input), howl);
			guard(rate, "howl", input, howl);
		}
		for( ReadyMadeChains.Named named : ReadyMadeChains.all() ) {
			this.index.put("ready." + key(named.name()) + ".name", named.name());
			this.index.put("ready." + key(named.name()) + ".chain", named.chain().toText());
		}
		this.index.put("block", Integer.toString(BLOCK));
		this.index.put("runs", Integer.toString(this.runs));
		try (Writer writer = Files.newBufferedWriter(FOLDER.resolve("index.properties"), StandardCharsets.UTF_8)) {
			writer.write("# GuitarFX's Java references (spec 027, T005), written by GuitarFxReferenceTest\n");
			for( Map.Entry<String, String> entry : this.index.entrySet() ) {
				writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
			}
		}

		assertTrue(Files.isRegularFile(FOLDER.resolve("index.properties")));
		int listed = 0;
		for( Map.Entry<String, String> entry : this.index.entrySet() ) {
			if( entry.getKey().endsWith(".input") || entry.getKey().endsWith(".output") ) {
				assertTrue(Files.isRegularFile(FOLDER.resolve(entry.getValue())), entry.getValue() + " is written");
			}
			if( entry.getKey().endsWith(".output") ) {
				listed++;
			}
		}
		assertEquals(this.runs, listed, "every run is in the index");
		assertEquals(4, ReadyMadeChains.all().size());
	}

	/** Each pedal: the defaults, each knob at its minimum and maximum, and every knob moved to its maximum in the middle. */
	private void pedals(float rate, String signal, String input, float[] samples) throws IOException {
		for( EffectKind kind : EffectKind.values() ) {
			for( Variant variant : variants(kind) ) {
				Pedal pedal = Pedals.create(kind);
				pedal.prepare(rate, BLOCK);
				pedal.set(variant.first);
				float[] out = run(pedal, samples, variant.second == null ? null : () -> pedal.set(variant.second));
				record(kind.textName(), variant.name, rate, signal, input, variant.knobs(), out);
			}
		}
	}

	private static List<Variant> variants(EffectKind kind) {
		List<Variant> variants = new ArrayList<Variant>();
		List<Knob> knobs = kind.knobs();
		double[] defaults = new double[knobs.size()];
		double[] maxima = new double[knobs.size()];
		for( int i = 0 ; i < knobs.size() ; i++ ) {
			defaults[i] = knobs.get(i).defaultValue();
			maxima[i] = knobs.get(i).max();
		}
		variants.add(new Variant("default", PedalSettings.of(kind, true, defaults), null));
		for( int i = 0 ; i < knobs.size() ; i++ ) {
			double[] low = defaults.clone();
			low[i] = knobs.get(i).min();
			variants.add(new Variant(knobs.get(i).key() + "-min", PedalSettings.of(kind, true, low), null));
			double[] high = defaults.clone();
			high[i] = knobs.get(i).max();
			variants.add(new Variant(knobs.get(i).key() + "-max", PedalSettings.of(kind, true, high), null));
		}
		variants.add(new Variant("moved", PedalSettings.of(kind, true, defaults), PedalSettings.of(kind, true, maxima)));
		return variants;
	}

	/** The amp in each character: the defaults, each knob at 0 and 10, every knob moved to 10 in the middle, and a change of character. */
	private void amp(float rate, String signal, String input, float[] samples) throws IOException {
		for( AmpSettings.Character character : AmpSettings.Character.values() ) {
			AmpSettings base = AmpSettings.defaults().withCharacter(character);
			List<Object[]> variants = new ArrayList<Object[]>();
			variants.add(new Object[] { "default", base, null });
			String[] names = { "gain", "bass", "middle", "treble", "volume" };
			for( int i = 0 ; i < names.length ; i++ ) {
				variants.add(new Object[] { names[i] + "-min", knob(base, i, 0), null });
				variants.add(new Object[] { names[i] + "-max", knob(base, i, 10), null });
			}
			AmpSettings loudest = base;
			for( int i = 0 ; i < names.length ; i++ ) {
				loudest = knob(loudest, i, 10);
			}
			variants.add(new Object[] { "moved", base, loudest });
			AmpSettings.Character next = AmpSettings.Character.values()[(character.ordinal() + 1) % AmpSettings.Character.values().length];
			variants.add(new Object[] { "character-" + next.textName(), base, base.withCharacter(next) });
			for( Object[] variant : variants ) {
				Amp amp = new Amp();
				amp.prepare(rate, BLOCK);
				amp.set((AmpSettings) variant[1]);
				AmpSettings second = (AmpSettings) variant[2];
				float[] out = run(amp, samples, second == null ? null : () -> amp.set(second));
				record("amp-" + character.textName(), (String) variant[0], rate, signal, input, ampKnobs((AmpSettings) variant[1], second), out);
			}
		}
	}

	private static AmpSettings knob(AmpSettings value, int index, double knob) {
		switch( index ) {
		case 0:
			return value.withGain(knob);
		case 1:
			return value.withBass(knob);
		case 2:
			return value.withMiddle(knob);
		case 3:
			return value.withTreble(knob);
		default:
			return value.withVolume(knob);
		}
	}

	private static String ampKnobs(AmpSettings first, AmpSettings second) {
		String text = ampText(first);
		return second == null ? text : text + ">" + ampText(second);
	}

	private static String ampText(AmpSettings value) {
		return value.character().textName() + "," + number(value.gain()) + "," + number(value.bass()) + "," + number(value.middle()) + ","
				+ number(value.treble()) + "," + number(value.volume());
	}

	/** The cabinet on, off, and switched off then on in the middle. */
	private void cabinet(float rate, String signal, String input, float[] samples) throws IOException {
		boolean[][] cases = { { true, true }, { false, false }, { true, false }, { false, true } };
		String[] names = { "on", "off", "switched-off", "switched-on" };
		for( int c = 0 ; c < cases.length ; c++ ) {
			final boolean second = cases[c][1];
			Cabinet cabinet = new Cabinet();
			cabinet.setEnabled(cases[c][0]);
			cabinet.prepare(rate, BLOCK);
			float[] out = run(cabinet, samples, cases[c][0] == second ? null : () -> cabinet.setEnabled(second));
			record("cabinet", names[c], rate, signal, input, cases[c][0] + ">" + second, out);
		}
	}

	/** The biquad in each of its five shapes, and gliding from one frequency to another in the middle. */
	private void filters(float rate, String signal, String input, float[] samples) throws IOException {
		String[] names = { "low-pass", "high-pass", "peak", "low-shelf", "high-shelf", "glide" };
		for( int c = 0 ; c < names.length ; c++ ) {
			Biquad biquad = new Biquad();
			biquad.prepare(rate, BLOCK);
			Runnable change = null;
			switch( c ) {
			case 0:
				biquad.setLowPass(1000, Math.sqrt(0.5));
				break;
			case 1:
				biquad.setHighPass(100, Math.sqrt(0.5));
				break;
			case 2:
				biquad.setPeak(700, 0.8, 6);
				break;
			case 3:
				biquad.setLowShelf(200, 6);
				break;
			case 4:
				biquad.setHighShelf(1800, -6);
				break;
			default:
				biquad.setPeak(500, 1, -6);
				change = () -> biquad.setPeak(3000, 2, 9);
			}
			float[] out = run(biquad, samples, change);
			record("biquad", names[c], rate, signal, input, names[c], out);
		}
	}

	/** Each oversampler factor, up then down. */
	private void oversamplers(float rate, String signal, String input, float[] samples) throws IOException {
		for( int factor : new int[] { 1, 2, 4 } ) {
			Oversampler oversampler = new Oversampler(factor, BLOCK);
			float[] high = new float[BLOCK * factor];
			float[] out = new float[samples.length];
			float[] block = new float[BLOCK];
			for( int offset = 0 ; offset < samples.length ; offset += BLOCK ) {
				int n = Math.min(BLOCK, samples.length - offset);
				System.arraycopy(samples, offset, block, 0, n);
				int count = oversampler.up(block, n, high);
				oversampler.down(high, count, block);
				System.arraycopy(block, 0, out, offset, n);
			}
			record("oversampler", "factor-" + factor, rate, signal, input, Integer.toString(factor), out);
		}
	}

	/** The output stage: faded in at its default volume, at 0 and 1, and faded out from the middle. */
	private void outputStage(float rate, String signal, String input, float[] samples) throws IOException {
		Object[][] cases = { { "default", null, false }, { "volume-min", 0.0, false }, { "volume-max", 1.0, false }, { "stopped", null, true } };
		for( Object[] variant : cases ) {
			OutputStage stage = new OutputStage(rate, BLOCK);
			if( variant[1] != null ) {
				stage.setVolume((Double) variant[1]);
			}
			stage.start();
			float[] out = samples.clone();
			float[] block = new float[BLOCK];
			for( int offset = 0 ; offset < out.length ; offset += BLOCK ) {
				if( (Boolean) variant[2] && offset == middle(out.length) ) {
					stage.stop();
				}
				int n = Math.min(BLOCK, out.length - offset);
				System.arraycopy(out, offset, block, 0, n);
				stage.process(block, n);
				System.arraycopy(block, 0, out, offset, n);
			}
			record("output-stage", (String) variant[0], rate, signal, input, variant[1] == null ? "0.7" : number((Double) variant[1]), out);
		}
	}

	/** The whole chain of each ready-made sound, and one sound changed for the next in the middle. */
	private void chains(float rate, String signal, String input, float[] samples) throws IOException {
		List<ReadyMadeChains.Named> all = ReadyMadeChains.all();
		for( int i = 0 ; i < all.size() ; i++ ) {
			ChainSettings chain = all.get(i).chain();
			float[] out = GuitarFxReferenceRuns.chain(chain, null, 0, rate, samples);
			record("chain", key(all.get(i).name()), rate, signal, input, chain.toText(), out);
			ChainSettings next = all.get((i + 1) % all.size()).chain();
			float[] changed = GuitarFxReferenceRuns.chain(chain, next, middle(samples.length), rate, samples);
			record("chain", key(all.get(i).name()) + "-then-" + key(all.get((i + 1) % all.size()).name()), rate, signal, input,
					chain.toText() + ">" + next.toText(), changed);
		}
	}

	private void guard(float rate, String signal, String input, float[] samples) throws IOException {
		record("feedback-guard", "default", rate, signal, input, "", GuitarFxReferenceRuns.guard(rate, samples));
	}

	private static float[] howl(float rate) {
		int frames = Math.round(1.5f * rate);
		float[] samples = new float[frames];
		for( int i = 0 ; i < frames ; i++ ) {
			double db = -40 + 34 * Math.min(1.0, i / (double) rate);
			samples[i] = (float) (Math.pow(10, db / 20) * Math.sin(2 * Math.PI * 1000 * i / rate));
		}
		return samples;
	}

	/** In blocks of {@link #BLOCK}; {@code change} runs before the block that starts in the middle. */
	private static float[] run(app.tuxguitar.audio.engine.monitor.Effect effect, float[] samples, Runnable change) {
		float[] out = new float[samples.length];
		float[] in = new float[BLOCK];
		float[] block = new float[BLOCK];
		for( int offset = 0 ; offset < samples.length ; offset += BLOCK ) {
			if( change != null && offset == middle(samples.length) ) {
				change.run();
			}
			int n = Math.min(BLOCK, samples.length - offset);
			System.arraycopy(samples, offset, in, 0, n);
			effect.process(in, block, n);
			System.arraycopy(block, 0, out, offset, n);
		}
		return out;
	}

	/** The first block start at or after the middle. */
	static int middle(int frames) {
		return (frames / 2 + BLOCK - 1) / BLOCK * BLOCK;
	}

	private void record(String element, String variant, float rate, String signal, String input, String knobs, float[] out) throws IOException {
		String id = element + "." + variant + "." + (int) rate + "." + signal;
		String output = "outputs/" + id + ".f32";
		write(FOLDER.resolve(output), out);
		String prefix = "run." + id + ".";
		this.index.put(prefix + "element", element);
		this.index.put(prefix + "variant", variant);
		this.index.put(prefix + "rate", Integer.toString((int) rate));
		this.index.put(prefix + "signal", signal);
		this.index.put(prefix + "knobs", knobs);
		this.index.put(prefix + "change-at", Integer.toString(middle(out.length)));
		this.index.put(prefix + "input", input);
		this.index.put(prefix + "output", output);
		this.runs++;
	}

	private static void write(Path file, float[] samples) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(samples.length * 4).order(ByteOrder.LITTLE_ENDIAN);
		for( float sample : samples ) {
			buffer.putFloat(sample);
		}
		try (OutputStream stream = Files.newOutputStream(file)) {
			stream.write(buffer.array());
		}
	}

	private static String key(String name) {
		return name.toLowerCase().replace(' ', '-');
	}

	private static String number(double value) {
		return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	private static final class Variant {
		final String name;
		final PedalSettings first;
		final PedalSettings second;

		Variant(String name, PedalSettings first, PedalSettings second) {
			this.name = name;
			this.first = first;
			this.second = second;
		}

		String knobs() {
			return text(this.first) + (this.second == null ? "" : ">" + text(this.second));
		}

		private static String text(PedalSettings pedal) {
			StringBuilder text = new StringBuilder();
			for( int i = 0 ; i < pedal.kind().knobs().size() ; i++ ) {
				text.append(i == 0 ? "" : ",").append(number(pedal.value(i)));
			}
			return text.toString();
		}
	}
}
