package app.tuxguitar.audio.engine.calibration;

import java.util.Map;

/** Where the profile library lives: a TuxGuitar configuration file of its own in the application, a map in tests (spec 013). */
public interface ProfilePersistence {

	Map<String, String> read();

	/** Replaces the whole content; a failure is an exception and leaves the previous content. */
	void write(Map<String, String> values);
}
