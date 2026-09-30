package app.tuxguitar.io.gtp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.models.TGTempo;

public class TestGTPTempo {

	private TGTempo tempo(int quarterValue) {
		TGTempo tempo = new TGFactory().newTempo();
		tempo.setQuarterValue(quarterValue);
		return tempo;
	}

	@Test
	public void aRealTempoIsApplied() {
		TGTempo tempo = tempo(120);
		GTPInputStream.applyTempo(tempo, 90);
		assertEquals(90, tempo.getQuarterValue());
	}

	@Test
	public void aTempoOfZeroKeepsTheCurrentTempo() {
		// files with a tempo of 0 (initial or in a mix-table change) made the measure duration divide by zero
		TGTempo tempo = tempo(120);
		GTPInputStream.applyTempo(tempo, 0);
		assertEquals(120, tempo.getQuarterValue());
	}

	@Test
	public void aNegativeTempoKeepsTheCurrentTempo() {
		TGTempo tempo = tempo(120);
		GTPInputStream.applyTempo(tempo, -1);
		assertEquals(120, tempo.getQuarterValue());
	}
}
