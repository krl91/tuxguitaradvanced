package app.tuxguitar.audio.engine.input;

import java.util.OptionalInt;

/**
 * One logical audio input: a mono device, or one channel of a multi-channel device.
 * Immutable.
 */
public final class AudioInputDescriptor {

	public enum Kind {
		BUILT_IN_MICROPHONE,
		MICROPHONE,
		INSTRUMENT,
		UNKNOWN
	}

	private final String id;
	private final String deviceName;
	private final Integer channelIndex;
	private final int channelCount;
	private final String displayName;
	private final Kind kind;
	private final boolean systemDefault;

	/**
	 * @param id stable key within the run (mixer name + index + channel)
	 * @param deviceName mixer name from Java Sound
	 * @param channelIndex 0-based channel within the device stream; null for mono devices
	 * @param channelCount channels the device offers (1 for mono)
	 * @param displayName device name, plus "— Input k" for multi-channel devices
	 * @param kind input kind; only the built-in microphone is expected to be positively identified
	 * @param systemDefault whether this is the system default input
	 */
	public AudioInputDescriptor(String id, String deviceName, Integer channelIndex, int channelCount, String displayName, Kind kind, boolean systemDefault) {
		this.id = id;
		this.deviceName = deviceName;
		this.channelIndex = channelIndex;
		this.channelCount = channelCount;
		this.displayName = displayName;
		this.kind = kind;
		this.systemDefault = systemDefault;
	}

	public String getId() {
		return this.id;
	}

	public String getDeviceName() {
		return this.deviceName;
	}

	/** Empty for mono devices. */
	public OptionalInt getChannelIndex() {
		return (this.channelIndex != null ? OptionalInt.of(this.channelIndex) : OptionalInt.empty());
	}

	public int getChannelCount() {
		return this.channelCount;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public Kind getKind() {
		return this.kind;
	}

	public boolean isSystemDefault() {
		return this.systemDefault;
	}
}
