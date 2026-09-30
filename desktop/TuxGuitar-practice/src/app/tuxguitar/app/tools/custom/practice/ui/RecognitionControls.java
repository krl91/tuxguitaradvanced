package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.List;

import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;

/**
 * Display-independent parts of the audio-recognition rubric (task T102): how a numeric setting
 * maps to an integer slider, how values are written for the learner, the message keys of each
 * setting, and the rubric layout limits. Kept free of widgets so it can be tested.
 */
final class RecognitionControls {

	/** Rows a section may have (settings + restore button), so the Configuration window stays on screen. */
	static final int MAX_SECTION_ROWS = 8;
	/** Width of the slider column and of wrapped explanations, in pixels. */
	static final float SLIDER_WIDTH = 220f;
	static final float TEXT_WIDTH = 420f;

	private RecognitionControls() {
	}

	/** Slider steps per displayed unit: whole units, or tenths for fractional values in dB. */
	static int resolution(RecognitionParameter.Number parameter) {
		if( parameter.isInteger() || parameter.getDisplayFactor() != 1.0 ) {
			return 1;
		}
		return 10;
	}

	/**
	 * Slider positions always run from 0 to this value. SWT's {@code Scale} ignores a negative
	 * minimum, a minimum not below its current maximum and a maximum not above its current
	 * minimum (checked in SWT 4.37 cocoa); setting the real bounds (−60…−25 dBFS, 300…1500 ms)
	 * left the default 0…100 range, froze the shown value and sent other sliders to the left
	 * (P13). Positions are therefore offsets from the parameter's minimum.
	 */
	static int sliderMaximum(RecognitionParameter.Number parameter) {
		return scaled(parameter, parameter.getMaximum()) - scaled(parameter, parameter.getMinimum());
	}

	/** The slider position of a value (0 = the parameter's minimum). */
	static int toSlider(RecognitionParameter.Number parameter, double value) {
		return (int) Math.round(value * steps(parameter)) - scaled(parameter, parameter.getMinimum());
	}

	/**
	 * The stored value for a slider position, validated again by the settings. Computed as one
	 * exact division ((minimum steps + position) / steps per unit) so that positions give exact
	 * values: 80 % is exactly 0.8, not 0.5 + 0.3.
	 */
	static double fromSlider(RecognitionParameter.Number parameter, int position) {
		return (scaled(parameter, parameter.getMinimum()) + position) / steps(parameter);
	}

	private static double steps(RecognitionParameter.Number parameter) {
		return parameter.getDisplayFactor() * resolution(parameter);
	}

	private static int scaled(RecognitionParameter.Number parameter, double value) {
		return (int) Math.round(value * steps(parameter));
	}

	/** The value as shown: "35", "80", "4.5", "2", "33". */
	static String displayNumber(RecognitionParameter.Number parameter, double value) {
		double shown = value * parameter.getDisplayFactor();
		if( resolution(parameter) == 1 ) {
			return Long.toString(Math.round(shown));
		}
		double tenths = Math.round(shown * 10.0) / 10.0;
		// "2" rather than "2.0"
		return (tenths == Math.rint(tenths) ? Long.toString(Math.round(tenths)) : String.valueOf(tenths));
	}

	/** Message key of a unit ("recognition.unit.cents", "recognition.unit.percent", …); null when none. */
	static String unitKey(String unit) {
		if( unit == null || unit.isEmpty() ) {
			return null;
		}
		return "recognition.unit." + (unit.equals("%") ? "percent" : unit);
	}

	static String nameKey(RecognitionParameter parameter) {
		return parameter.getKey() + ".name";
	}

	static String tipKey(RecognitionParameter parameter) {
		return parameter.getKey() + ".tip";
	}

	static String optionKey(RecognitionParameter.Choice parameter, String option) {
		return parameter.getKey() + ".option." + option;
	}

	static String sectionKey(RecognitionParameter.Section section) {
		return "recognition.section." + section.name();
	}

	/** Rows of a section in the rubric: one per setting, plus the Input selector and the restore button. */
	static int rows(RecognitionParameter.Section section) {
		List<RecognitionParameter> parameters = RecognitionParameters.of(section);
		int rows = parameters.size() + 1;
		if( section == RecognitionParameter.Section.INPUT ) {
			// the shared input selector and its note
			rows += 2;
		}
		return rows;
	}
}
