package app.tuxguitar.audio.engine.pitch;

import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.input.InputAssessment;

/** Converts frequency to sounding-pitch note, octave and cent offset. */
public final class NoteInterpreter {

	private static final String[] NOTE_NAMES = {
		"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
	};

	private final double confidenceThreshold;

	public NoteInterpreter(double confidenceThreshold) {
		if (!Double.isFinite(confidenceThreshold) || confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
			throw new IllegalArgumentException("confidenceThreshold must be between 0 and 1");
		}
		this.confidenceThreshold = confidenceThreshold;
	}

	public PitchReading interpret(PitchEstimate estimate, InputAssessment assessment) {
		if (estimate == null || !estimate.isPitched()) {
			return PitchReading.noReading();
		}
		return interpret(estimate.getF0Hz(), estimate.getProbability(), assessment);
	}

	public PitchReading interpret(double f0Hz, double confidence, InputAssessment assessment) {
		if (!Double.isFinite(f0Hz) || f0Hz <= 0.0) {
			return PitchReading.noReading();
		}
		if (assessment == null) {
			throw new IllegalArgumentException("assessment is required");
		}

		double exactMidi = 69.0 + 12.0 * log2(f0Hz / 440.0);
		int midi = (int) Math.round(exactMidi);
		if (midi < 0 || midi > 127) {
			return PitchReading.noReading();
		}
		double noteFrequency = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
		double cents = 1200.0 * log2(f0Hz / noteFrequency);
		boolean reliable = Double.isFinite(confidence)
				&& confidence >= this.confidenceThreshold
				&& !assessment.isUnusable();
		return new PitchReading(midi, NOTE_NAMES[midi % 12], midi / 12 - 1, cents, reliable);
	}

	public double getConfidenceThreshold() {
		return this.confidenceThreshold;
	}

	private static double log2(double value) {
		return Math.log(value) / Math.log(2.0);
	}
}
