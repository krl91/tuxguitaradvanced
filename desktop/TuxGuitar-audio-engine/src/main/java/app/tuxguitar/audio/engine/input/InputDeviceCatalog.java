package app.tuxguitar.audio.engine.input;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/** Enumerates logical Java Sound capture inputs without opening them. */
public final class InputDeviceCatalog {

	public List<AudioInputDescriptor> listInputs() {
		Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
		Mixer.Info defaultInfo = defaultMixerInfo();
		List<AudioInputDescriptor> inputs = new ArrayList<AudioInputDescriptor>();
		for (int mixerIndex = 0; mixerIndex < mixerInfos.length; mixerIndex++) {
			Mixer mixer = AudioSystem.getMixer(mixerInfos[mixerIndex]);
			int channels = advertised16BitChannelCount(mixer);
			if (channels == 0) {
				continue;
			}
			boolean systemDefault = sameMixer(mixerInfos[mixerIndex], defaultInfo)
					|| "Default Audio Device".equalsIgnoreCase(mixerInfos[mixerIndex].getName());
			for (int channel = 0; channel < channels; channel++) {
				Integer channelIndex = channels == 1 ? null : Integer.valueOf(channel);
				String displayName = channels == 1
						? mixerInfos[mixerIndex].getName()
						: mixerInfos[mixerIndex].getName() + " — Input " + (channel + 1);
				inputs.add(new AudioInputDescriptor(
						id(mixerIndex, mixerInfos[mixerIndex].getName(), channelIndex),
						mixerInfos[mixerIndex].getName(),
						channelIndex,
						channels,
						displayName,
						classify(mixerInfos[mixerIndex].getName()),
						systemDefault));
			}
		}
		inputs.sort(Comparator.comparing(AudioInputDescriptor::isSystemDefault).reversed());
		return inputs;
	}

	Mixer resolveMixer(AudioInputDescriptor descriptor) {
		int mixerIndex = mixerIndex(descriptor.getId());
		Mixer.Info[] infos = AudioSystem.getMixerInfo();
		if (mixerIndex < 0 || mixerIndex >= infos.length
				|| !infos[mixerIndex].getName().equals(descriptor.getDeviceName())) {
			throw new IllegalArgumentException("audio input is no longer available: " + descriptor.getDeviceName());
		}
		return AudioSystem.getMixer(infos[mixerIndex]);
	}

	List<AudioFormat> advertisedFormats(AudioInputDescriptor descriptor) {
		List<AudioFormat> formats = new ArrayList<AudioFormat>();
		for (Line.Info info : resolveMixer(descriptor).getTargetLineInfo()) {
			if (info instanceof DataLine.Info && TargetDataLine.class.isAssignableFrom(info.getLineClass())) {
				for (AudioFormat format : ((DataLine.Info) info).getFormats()) {
					if (isPcm(format) && (format.getSampleSizeInBits() == 16 || format.getSampleSizeInBits() == 24)
							&& format.getChannels() == descriptor.getChannelCount()) {
						formats.add(format);
					}
				}
			}
		}
		return formats;
	}

	static AudioInputDescriptor.Kind classify(String deviceName) {
		String normalized = deviceName.toLowerCase(Locale.ROOT);
		boolean microphone = normalized.contains("microphone") || normalized.contains("micro");
		boolean builtIn = normalized.contains("built-in") || normalized.contains("internal") || normalized.contains("macbook");
		return microphone && builtIn
				? AudioInputDescriptor.Kind.BUILT_IN_MICROPHONE
				: AudioInputDescriptor.Kind.UNKNOWN;
	}

	private static int advertised16BitChannelCount(Mixer mixer) {
		int channels = 0;
		for (Line.Info info : mixer.getTargetLineInfo()) {
			if (info instanceof DataLine.Info && TargetDataLine.class.isAssignableFrom(info.getLineClass())) {
				for (AudioFormat format : ((DataLine.Info) info).getFormats()) {
					if (isPcm(format) && format.getSampleSizeInBits() == 16 && format.getChannels() > 0) {
						channels = Math.max(channels, format.getChannels());
					}
				}
			}
		}
		return channels;
	}

	private static boolean isPcm(AudioFormat format) {
		return AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
				|| AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding());
	}

	private static Mixer.Info defaultMixerInfo() {
		try {
			return AudioSystem.getMixer(null).getMixerInfo();
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static boolean sameMixer(Mixer.Info left, Mixer.Info right) {
		return right != null
				&& left.getName().equals(right.getName())
				&& left.getVendor().equals(right.getVendor())
				&& left.getDescription().equals(right.getDescription())
				&& left.getVersion().equals(right.getVersion());
	}

	private static String id(int mixerIndex, String mixerName, Integer channelIndex) {
		return mixerIndex + ":" + mixerName + ":" + (channelIndex == null ? "mono" : channelIndex);
	}

	private static int mixerIndex(String id) {
		int separator = id.indexOf(':');
		if (separator <= 0) {
			throw new IllegalArgumentException("invalid audio input id: " + id);
		}
		try {
			return Integer.parseInt(id.substring(0, separator));
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("invalid audio input id: " + id, exception);
		}
	}
}
