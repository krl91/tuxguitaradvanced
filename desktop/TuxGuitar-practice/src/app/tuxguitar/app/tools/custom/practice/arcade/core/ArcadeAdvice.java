package app.tuxguitar.app.tools.custom.practice.arcade.core;

import app.tuxguitar.audio.engine.input.AudioInputDescriptor;

/**
 * What Arcade tells the learner about the audio set-up (spec 003 FR-013). The backing is never judged as the learner's playing, but an input that is a
 * microphone (or of unknown kind) can pick up the speakers and make the backing be taken for the guitar: headphones are advised then. The rule is
 * the one of the practice window (a direct instrument input needs no advice).
 */
public final class ArcadeAdvice {

	private ArcadeAdvice() {
	}

	/** True when the input could hear the sound of the computer and the learner is advised to wear headphones. */
	public static boolean headphonesAdvised(AudioInputDescriptor.Kind kind) {
		return kind != AudioInputDescriptor.Kind.INSTRUMENT;
	}

	/** The same for an input that is not selected yet: no input, no advice to give. */
	public static boolean headphonesAdvised(AudioInputDescriptor input) {
		return input != null && headphonesAdvised(input.getKind());
	}
}
