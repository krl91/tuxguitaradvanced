package app.tuxguitar.audio.engine.technique;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.TechniqueParameters;

/**
 * Each technique value takes effect (spec 026, T026, SC-001): on synthetic note segments (no audio), every detector value of the five sections at one
 * end of its range changes a verdict or a measurement of the detectors built from the settings. The segments: wobbles of several depths, rates and
 * lengths, rises and glides of several sizes and speeds, steps, partly unvoiced and outlying pitches, short segments, level rises at the start, and
 * written bends, pre-bends and releases made under, on and over their target. The frame confidence is a session value, checked in practice
 * ({@code TechniqueFrameTakesEffectTest}).
 */
public class TechniqueTakesEffectTest {

	private static final double HOP = 256.0 / 44100.0;

	@Test
	public void everyTechniqueValueChangesAVerdict() {
		String recommended = decisions(RecognitionSettings.defaults());
		List<String> withoutEffect = new ArrayList<String>();
		for( Section section : new Section[] { Section.TECHNIQUE_1, Section.TECHNIQUE_2, Section.TECHNIQUE_3, Section.TECHNIQUE_4, Section.TECHNIQUE_5 } ) {
			for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
				if( parameter == RecognitionParameters.TECHNIQUE_FRAME_CONFIDENCE ) {
					continue;
				}
				RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
				boolean changed = false;
				for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
					changed |= !decisions(RecognitionSettings.defaults().with(number, end)).equals(recommended);
				}
				if( !changed ) {
					withoutEffect.add(number.getKey());
				}
			}
		}
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change no verdict");
	}

	private static String decisions(RecognitionSettings settings) {
		TechniqueDetector[] detectors = { new VibratoDetector(TechniqueParameters.vibrato(settings)), new BendDetector(TechniqueParameters.bend(settings)),
				new SlideDetector(TechniqueParameters.slide(settings)), new LegatoDetector(TechniqueParameters.legato(settings)) };
		StringBuilder s = new StringBuilder();
		for( NoteSegment segment : segments() ) {
			for( TechniqueDetector detector : detectors ) {
				s.append(text(detector.evaluate(segment))).append(' ');
			}
			s.append('\n');
		}
		BendDetector bend = new BendDetector(TechniqueParameters.bend(settings));
		for( double written : new double[] { 0.1, 0.2, 0.3, 0.5, 1.0, 2.0 } ) {
			for( double made : new double[] { 0.2, 0.4, 0.6, 0.8, 0.9, 1.0, 1.1, 1.2, 1.5 } ) {
				// a bend, a release from the bent pitch, a pre-bend that starts off its pitch
				s.append(text(bend.evaluate(pitch(0.8, t -> 60.0 + written * made * Math.min(1.0, t / 0.4)), 60.0, BendPath.of(0.0, 0.0, 0.5, written))));
				s.append(text(bend.evaluate(pitch(0.8, t -> 60.0 + written * made - written * Math.min(1.0, t / 0.4)), 60.0,
						BendPath.of(0.0, written, 0.5, 0.0))));
				s.append(text(bend.evaluate(pitch(0.8, t -> 60.0 + written * made), 60.0, BendPath.of(0.0, written, 1.0, written))));
				// a bend reached late, after a slow start
				s.append(text(bend.evaluate(pitch(0.8, t -> 60.0 + written * made * Math.max(0.0, Math.min(1.0, (t - 0.05) / 0.7))), 60.0,
						BendPath.of(0.0, 0.0, 0.9, written))));
				s.append('\n');
			}
		}
		return s.toString();
	}

	private static List<NoteSegment> segments() {
		List<NoteSegment> segments = new ArrayList<NoteSegment>();
		for( double depth : new double[] { 0.03, 0.06, 0.1, 0.15, 0.25, 0.4, 0.6 } ) {
			for( double rate : new double[] { 0.8, 1.5, 2.2, 3.0, 5.0, 7.0, 9.5, 12.0, 16.0, 20.0 } ) {
				for( double seconds : new double[] { 0.12, 0.25, 0.4, 0.8, 1.5, 2.5 } ) {
					segments.add(pitch(seconds, t -> 60.0 + depth * Math.sin(2 * Math.PI * rate * t)));
				}
			}
		}
		for( double cents : new double[] { 8, 15, 25, 35, 45, 55, 70, 90, 110, 130, 160, 200, 260, 320 } ) {
			for( double seconds : new double[] { 0.08, 0.12, 0.18, 0.3, 0.6, 1.2 } ) {
				segments.add(pitch(seconds, t -> 60.0 + cents / 100.0 * t / seconds));
				// a glide that comes back part of the way: less monotonous
				segments.add(pitch(seconds, t -> 60.0 + cents / 100.0 * (t < 0.7 * seconds ? t / (0.7 * seconds) : 1.0 - (t - 0.7 * seconds) / seconds)));
				// a few large steps, a jump
				for( int steps : new int[] { 2, 4, 8, 14, 24 } ) {
					segments.add(pitch(seconds, t -> 60.0 + cents / 100.0 * Math.floor(t / seconds * steps) / steps));
				}
			}
		}
		for( double unvoiced : new double[] { 0.1, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.85 } ) {
			for( double cents : new double[] { 40, 80, 150 } ) {
				segments.add(pitch(0.8, t -> ((int) (t / HOP) * 7919 % 100) < unvoiced * 100 ? Double.NaN : 60.0 + cents / 100.0 * t / 0.8));
				segments.add(pitch(0.8, t -> ((int) (t / HOP) * 7919 % 100) < unvoiced * 100 ? Double.NaN : 60.0 + 0.3 * Math.sin(2 * Math.PI * 6.0 * t)));
			}
		}
		for( double outlier : new double[] { 1.5, 3.0, 4.5, 6.0, 9.0, 12.0 } ) {
			for( double share : new double[] { 0.1, 0.3 } ) {
				segments.add(pitch(0.8, t -> ((int) (t / HOP) * 7919 % 100) < share * 100 ? 60.0 + outlier : 60.0 + 0.3 * Math.sin(2 * Math.PI * 6.0 * t)));
				segments.add(pitch(0.8, t -> ((int) (t / HOP) * 7919 % 100) < share * 100 ? 60.0 - outlier : 60.0 + 0.8 * t / 0.8));
			}
		}
		for( int hops : new int[] { 3, 5, 7, 9, 12, 16, 22, 30, 38, 45 } ) {
			segments.add(pitch(hops * HOP, t -> 60.0 + 0.5 * Math.sin(2 * Math.PI * 6.0 * t) + 1.0 * t / (hops * HOP)));
			segments.add(pitch(hops * HOP, t -> 60.0 + 0.8 * t / (hops * HOP)));
			segments.add(pitchWithGaps(160, hops, t -> 60.0 + 0.5 * Math.sin(2 * Math.PI * 6.0 * t)));
			segments.add(pitchWithGaps(160, hops, t -> 60.0 + 1.2 * t / (160 * HOP)));
		}
		for( double rise : new double[] { 0.0, 0.5, 1.0, 2.0, 2.5, 3.5, 4.5, 5.5, 7.0, 9.0, 12.0, 16.0, 22.0 } ) {
			for( int at : new int[] { 0, 2, 4, 6, 9, 14, 20, 30, 50, 80 } ) {
				segments.add(levels(120, i -> i < at ? -30.0 : -30.0 + rise));
			}
		}
		return segments;
	}

	private static String text(TechniqueEvidence evidence) {
		StringBuilder s = new StringBuilder(evidence.getVerdict().name().substring(0, 2)).append(Math.round(evidence.getConfidence() * 1000));
		for( java.util.Map.Entry<String, Double> m : evidence.getMeasurements().entrySet() ) {
			s.append(',').append(Math.round(m.getValue() * 10));
		}
		return s.toString();
	}

	private static NoteSegment pitch(double seconds, DoubleUnaryOperator midiAt) {
		int n = Math.max(1, (int) Math.round(seconds / HOP));
		double[] midi = new double[n];
		double[] level = new double[n];
		for( int i = 0 ; i < n ; i ++ ) {
			midi[i] = midiAt.applyAsDouble(i * HOP);
			level[i] = -20.0;
		}
		return new NoteSegment(midi, level, new boolean[n], HOP);
	}

	/** {@code n} hops of which only {@code voiced} are voiced, spread evenly. */
	private static NoteSegment pitchWithGaps(int n, int voiced, DoubleUnaryOperator midiAt) {
		double[] midi = new double[n];
		double[] level = new double[n];
		for( int i = 0 ; i < n ; i ++ ) {
			midi[i] = (i * voiced / n != (i + 1) * voiced / n) ? midiAt.applyAsDouble(i * HOP) : Double.NaN;
			level[i] = -20.0;
		}
		return new NoteSegment(midi, level, new boolean[n], HOP);
	}

	private static NoteSegment levels(int n, java.util.function.IntToDoubleFunction levelAt) {
		double[] midi = new double[n];
		double[] level = new double[n];
		for( int i = 0 ; i < n ; i ++ ) {
			midi[i] = 60.0;
			level[i] = levelAt.applyAsDouble(i);
		}
		return new NoteSegment(midi, level, new boolean[n], HOP);
	}
}
