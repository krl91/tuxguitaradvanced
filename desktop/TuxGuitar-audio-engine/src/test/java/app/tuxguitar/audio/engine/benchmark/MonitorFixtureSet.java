package app.tuxguitar.audio.engine.benchmark;

import java.util.ArrayList;
import java.util.List;

/**
 * The recorded guitar inputs used by the monitor tests of spec 022 (task T009): every fixture of the manifest whose role is
 * {@code reference}, plus the silence. It opens {@link BenchmarkFixtures} (package-private) to the monitor tests without widening it.
 */
public final class MonitorFixtureSet {

	public static final class Input {
		private final String name;
		private final float[] samples;

		private Input(String name, float[] samples) {
			this.name = name;
			this.samples = samples;
		}

		public String name() {
			return this.name;
		}

		public float[] samples() {
			return this.samples.clone();
		}
	}

	private MonitorFixtureSet() {
	}

	public static List<Input> recognitionInputs() throws Exception {
		List<Input> inputs = new ArrayList<Input>();
		for( BenchmarkFixtures.Fixture fixture : BenchmarkFixtures.readManifest() ) {
			if( "reference".equals(fixture.role) || fixture.file.endsWith("/01_none_silence.wav") ) {
				inputs.add(new Input(fixture.file, BenchmarkFixtures.readRecording(fixture).samples));
			}
		}
		return inputs;
	}
}
