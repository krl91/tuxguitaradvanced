package app.tuxguitar.audio.engine.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.input.AudioInputDescriptor;

/** Spec 013 (T006): the key of a profile is the device's name, its channel and its rank among identical inputs, never the descriptor's id (research R3). */
public class DeviceKeyTest {

	private static AudioInputDescriptor input(int mixerIndex, String name, Integer channel, int channels) {
		String id = mixerIndex + ":" + name + ":" + (channel == null ? "mono" : channel);
		return new AudioInputDescriptor(id, name, channel, channels, name, AudioInputDescriptor.Kind.UNKNOWN, false);
	}

	@Test
	public void theKeyIsTheNameTheChannelAndTheRank() {
		assertEquals("Micro MacBook Pro#mono#0", DeviceKey.of("Micro MacBook Pro", null, 0).asString());
		assertEquals("Scarlett 2i2#1#0", DeviceKey.of("Scarlett 2i2", 1, 0).asString());
	}

	@Test
	public void theSameDeviceKeepsItsKeyWhenItsMixerIndexChanges() {
		AudioInputDescriptor before = input(0, "Micro MacBook Pro", null, 1);
		AudioInputDescriptor after = input(3, "Micro MacBook Pro", null, 1);
		List<AudioInputDescriptor> catalogBefore = Arrays.asList(before, input(1, "Other", null, 1));
		List<AudioInputDescriptor> catalogAfter = Arrays.asList(input(0, "Other", null, 1), input(1, "Zoom", null, 1), input(2, "Third", null, 1), after);
		assertEquals(DeviceKey.of(before, catalogBefore), DeviceKey.of(after, catalogAfter), "the id begins with the mixer index, the key does not");
	}

	@Test
	public void twoIdenticalAdaptersGetTwoKeys() {
		AudioInputDescriptor first = input(1, "USB Audio", null, 1);
		AudioInputDescriptor second = input(2, "USB Audio", null, 1);
		List<AudioInputDescriptor> catalog = Arrays.asList(input(0, "Micro MacBook Pro", null, 1), first, second);
		assertNotEquals(DeviceKey.of(first, catalog), DeviceKey.of(second, catalog));
		assertEquals("USB Audio#mono#0", DeviceKey.of(first, catalog).asString());
		assertEquals("USB Audio#mono#1", DeviceKey.of(second, catalog).asString());
	}

	@Test
	public void aDeviceWithTwoChannelsHasTwoKeysAndNoRankBetweenThem() {
		AudioInputDescriptor one = input(0, "Interface", 0, 2);
		AudioInputDescriptor two = input(0, "Interface", 1, 2);
		List<AudioInputDescriptor> catalog = Arrays.asList(one, two);
		assertNotEquals(DeviceKey.of(one, catalog), DeviceKey.of(two, catalog));
		assertEquals("Interface#0#0", DeviceKey.of(one, catalog).asString());
		assertEquals("Interface#1#0", DeviceKey.of(two, catalog).asString());
	}

	@Test
	public void theStorageIdIsSafeInAConfigurationKeyAndComesBackEqual() {
		for (String name : new String[] { "Micro MacBook Pro", "USB Audio #2: A.B=C", "Casque Bluetooth é — Input 1", "a.b.c", "100%" }) {
			DeviceKey key = DeviceKey.of(name, 1, 2);
			String id = key.storageId();
			assertEquals(-1, id.indexOf('.'), "no dot, the library separates its keys with dots: " + id);
			assertEquals(-1, id.indexOf(' '), id);
			assertEquals(-1, id.indexOf('='), id);
			assertEquals(-1, id.indexOf(':'), id);
			assertEquals(key, DeviceKey.fromStorageId(id), name);
		}
	}
}
