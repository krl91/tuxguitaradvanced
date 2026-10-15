package app.tuxguitar.audio.engine.calibration;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalInt;

import app.tuxguitar.audio.engine.input.AudioInputDescriptor;

/**
 * What a profile is kept under (spec 013, research R3): the device's name, its channel ("mono" or the channel index) and its rank among the catalog's
 * inputs that have the same name and channel (0 for the usual single device). Not the descriptor's id, which begins with the position of the mixer in the
 * system's list and may change between sessions.
 */
public final class DeviceKey {

	private final String name;
	private final Integer channel;
	private final int ordinal;

	private DeviceKey(String name, Integer channel, int ordinal) {
		if (name == null) {
			throw new IllegalArgumentException("the device name is required");
		}
		this.name = name;
		this.channel = channel;
		this.ordinal = Math.max(0, ordinal);
	}

	public static DeviceKey of(String deviceName, Integer channelIndex, int ordinal) {
		return new DeviceKey(deviceName, channelIndex, ordinal);
	}

	/** The key of {@code input} within {@code catalog}: its rank is the number of earlier inputs of the catalog with the same name and channel. */
	public static DeviceKey of(AudioInputDescriptor input, List<AudioInputDescriptor> catalog) {
		OptionalInt channelIndex = input.getChannelIndex();
		Integer channel = channelIndex.isPresent() ? Integer.valueOf(channelIndex.getAsInt()) : null;
		int ordinal = 0;
		for (AudioInputDescriptor other : catalog) {
			if (other.getId().equals(input.getId())) {
				break;
			}
			if (other.getDeviceName().equals(input.getDeviceName()) && sameChannel(other.getChannelIndex(), channelIndex)) {
				ordinal++;
			}
		}
		return new DeviceKey(input.getDeviceName(), channel, ordinal);
	}

	private static boolean sameChannel(OptionalInt a, OptionalInt b) {
		return a.isPresent() == b.isPresent() && (!a.isPresent() || a.getAsInt() == b.getAsInt());
	}

	public String getName() {
		return this.name;
	}

	public String asString() {
		return this.name + "#" + (this.channel == null ? "mono" : this.channel.toString()) + "#" + this.ordinal;
	}

	/** {@link #asString()} written with letters, digits, '_' and '-' only: safe in a configuration key and free of dots. */
	public String storageId() {
		byte[] bytes = asString().getBytes(StandardCharsets.UTF_8);
		StringBuilder id = new StringBuilder();
		for (byte b : bytes) {
			int c = b & 0xFF;
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
				id.append((char) c);
			} else {
				id.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16))).append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
			}
		}
		return id.toString();
	}

	/** The key a storage id was made from, or {@code null} when the text is not one. */
	public static DeviceKey fromStorageId(String storageId) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			for (int i = 0; i < storageId.length(); i++) {
				char c = storageId.charAt(i);
				if (c == '%') {
					bytes.write(Integer.parseInt(storageId.substring(i + 1, i + 3), 16));
					i += 2;
				} else {
					bytes.write(c);
				}
			}
			String text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
			int last = text.lastIndexOf('#');
			int middle = text.lastIndexOf('#', last - 1);
			if (last < 0 || middle < 0) {
				return null;
			}
			String channelText = text.substring(middle + 1, last);
			Integer channel = channelText.equals("mono") ? null : Integer.valueOf(channelText);
			return new DeviceKey(text.substring(0, middle), channel, Integer.parseInt(text.substring(last + 1)));
		} catch (RuntimeException exception) {
			return null;
		}
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof DeviceKey && asString().equals(((DeviceKey) other).asString());
	}

	@Override
	public int hashCode() {
		return asString().hashCode();
	}

	@Override
	public String toString() {
		return asString();
	}
}
