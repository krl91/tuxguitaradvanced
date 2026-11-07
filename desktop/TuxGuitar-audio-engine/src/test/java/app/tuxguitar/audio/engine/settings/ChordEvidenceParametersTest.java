package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.ChordEvidence;
import app.tuxguitar.audio.engine.analysis.PolyphonicAnalyzer;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;

/**
 * The chord-evidence family (spec 026, T017, inventory.md "CHORD_EVIDENCE"): its defaults are the former constants of the chord evidence and of the
 * strum meter, and the spectrum's parameters, the strum meter's and the native tuning array carry the settings.
 */
public class ChordEvidenceParametersTest {

	@Test
	public void theDefaultsAreTheFormerValues() {
		assertEquals(ChordEvidence.PARTIAL_PRESENCE_RATIO, RecognitionParameters.CHORD_EVIDENCE_PARTIAL_PRESENCE.getDefault());
		assertEquals(ChordEvidence.NEIGHBOUR_MARGIN, RecognitionParameters.CHORD_EVIDENCE_NEIGHBOUR_MARGIN.getDefault());
		PolyphonicAnalyzer.Parameters spectrum = PolyphonicAnalyzer.Parameters.defaults();
		assertEquals(ChordEvidence.PARTIAL_PRESENCE_RATIO, spectrum.partialPresenceRatio);
		assertEquals(ChordEvidence.NEIGHBOUR_MARGIN, spectrum.neighbourMargin);
		StrumClipMeter.Parameters strum = StrumClipMeter.Parameters.defaults();
		assertEquals(strum.windowNanos / 1_000_000.0, RecognitionParameters.CHORD_EVIDENCE_STRUM_WINDOW.getDefault());
		assertEquals(strum.armQuietHops, RecognitionParameters.CHORD_EVIDENCE_STRUM_QUIET.getDefault().intValue());
		assertEquals(strum.attackRiseDb, RecognitionParameters.CHORD_EVIDENCE_STRUM_RISE.getDefault());
		assertEquals(strum.clipShare, RecognitionParameters.CHORD_EVIDENCE_STRUM_CLIP_SHARE.getDefault());
		assertEquals(strums(strum), strums(StrumClipMeter.Parameters.of(RecognitionSettings.defaults())));
	}

	@Test
	public void theSpectrumTheMeterAndTheNativeArrayCarryTheSettings() {
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.CHORD_EVIDENCE_PARTIAL_PRESENCE, 0.1)
				.with(RecognitionParameters.CHORD_EVIDENCE_NEIGHBOUR_MARGIN, 2.5).with(RecognitionParameters.CHORD_EVIDENCE_STRUM_WINDOW, 500)
				.with(RecognitionParameters.CHORD_EVIDENCE_STRUM_QUIET, 100).with(RecognitionParameters.CHORD_EVIDENCE_STRUM_RISE, 6.0)
				.with(RecognitionParameters.CHORD_EVIDENCE_STRUM_CLIP_SHARE, 0.01);
		PolyphonicAnalyzer.Parameters p = PolyphonicAnalyzer.Parameters.of(s);
		assertEquals(0.1, p.partialPresenceRatio);
		assertEquals(2.5, p.neighbourMargin);
		assertEquals(Arrays.asList(500_000_000.0, 100.0, 6.0, 0.01), strums(StrumClipMeter.Parameters.of(s)));
		double[] tuning = NativeParameters.tuning(s);
		assertEquals(0.1, tuning[NativeParameters.T_CHORD_PARTIAL_PRESENCE]);
		assertEquals(2.5, tuning[NativeParameters.T_CHORD_NEIGHBOUR_MARGIN]);
		assertEquals(500.0, tuning[NativeParameters.T_STRUM_WINDOW]);
		assertEquals(100.0, tuning[NativeParameters.T_STRUM_QUIET]);
		assertEquals(6.0, tuning[NativeParameters.T_STRUM_RISE]);
		assertEquals(0.01, tuning[NativeParameters.T_STRUM_CLIP_SHARE]);
		PolyphonicAnalyzer.Parameters read = NativeParameters.polyphonic(tuning);
		assertEquals(0.1, read.partialPresenceRatio);
		assertEquals(2.5, read.neighbourMargin);
	}

	@Test
	public void theSectionHoldsItsSixValues() {
		assertEquals(Arrays.asList(RecognitionParameters.CHORD_EVIDENCE_PARTIAL_PRESENCE, RecognitionParameters.CHORD_EVIDENCE_NEIGHBOUR_MARGIN,
				RecognitionParameters.CHORD_EVIDENCE_STRUM_WINDOW, RecognitionParameters.CHORD_EVIDENCE_STRUM_QUIET,
				RecognitionParameters.CHORD_EVIDENCE_STRUM_RISE, RecognitionParameters.CHORD_EVIDENCE_STRUM_CLIP_SHARE),
				RecognitionParameters.of(Section.CHORD_EVIDENCE));
	}

	private static List<Double> strums(StrumClipMeter.Parameters p) {
		return Arrays.asList((double) p.windowNanos, (double) p.armQuietHops, p.attackRiseDb, p.clipShare);
	}
}
