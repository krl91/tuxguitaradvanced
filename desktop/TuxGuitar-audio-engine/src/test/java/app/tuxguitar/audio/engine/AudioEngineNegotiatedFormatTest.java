package app.tuxguitar.audio.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.sound.sampled.AudioFormat;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * Regression (spec 016, gate G1): every caller selects an input with no format, so the route of the practice window had no format, and the rhythm
 * calibration stayed greyed out and the record of a run had an empty route. The engine keeps the format the capture negotiated, for the input it was
 * negotiated for.
 */
public class AudioEngineNegotiatedFormatTest {

	@Test
	public void theFormatTheCaptureNegotiatedIsKeptForItsInputOnly() {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		try {
			AudioEngine engine = FakeAudioInput.newEngine(context, RecognitionSettings.defaults());
			AudioInputDescriptor rocksmith = new AudioInputDescriptor("usb", "Rocksmith", null, 1, "Rocksmith", AudioInputDescriptor.Kind.UNKNOWN, false);
			AudioInputDescriptor builtIn = new AudioInputDescriptor("mic", "Built-in", null, 1, "Built-in", AudioInputDescriptor.Kind.BUILT_IN_MICROPHONE, true);
			engine.getSelection().select(rocksmith, null);
			assertNull(engine.getNegotiatedFormat(rocksmith), "nothing negotiated yet");

			AudioFormat format = new AudioFormat(48000f, 16, 1, true, false);
			engine.formatNegotiated(format);
			assertEquals(format, engine.getNegotiatedFormat(rocksmith));
			assertNull(engine.getNegotiatedFormat(builtIn), "negotiated for another input");
			assertNull(engine.getNegotiatedFormat(null));
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}
}
