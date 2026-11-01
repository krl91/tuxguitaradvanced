package app.tuxguitar.audio.engine.monitor;

import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/**
 * The output devices the amp window offers (spec 022, contracts/amp-window.md): the system's default ({@code ""}) first, then each device that plays sound, by
 * the name the system gives it. The native device finds an output by that name (or a name containing it), else plays on the default.
 */
public final class OutputDevices {

	private OutputDevices() {
	}

	public static List<String> names() {
		List<String> names = new ArrayList<String>();
		names.add("");
		try {
			for (Mixer.Info info : AudioSystem.getMixerInfo()) {
				Mixer mixer = AudioSystem.getMixer(info);
				if (mixer.isLineSupported(new Line.Info(SourceDataLine.class)) && !names.contains(info.getName()) && !isDefault(info.getName())) {
					names.add(info.getName());
				}
			}
		} catch (RuntimeException exception) {
			// no list from the system: the default output only
		}
		return names;
	}

	private static boolean isDefault(String name) {
		return name.startsWith("Default Audio Device") || name.equals("Primary Sound Driver") || name.startsWith("Port ");
	}
}
