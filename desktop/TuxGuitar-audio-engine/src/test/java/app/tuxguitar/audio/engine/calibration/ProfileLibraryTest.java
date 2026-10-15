package app.tuxguitar.audio.engine.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.util.TGContext;

/** Spec 013 (T013): the library of profiles, one per device, in its own file (FR-007, FR-012, FR-013, FR-019, SC-002). */
public class ProfileLibraryTest {

	/** An in-memory configuration file. */
	static final class Memory implements ProfilePersistence {
		Map<String, String> values = new HashMap<String, String>();
		int writes;
		boolean failWrite;
		boolean failRead;

		public Map<String, String> read() {
			if (this.failRead) {
				throw new IllegalStateException("unreadable");
			}
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.writes++;
			if (this.failWrite) {
				throw new IllegalStateException("read-only");
			}
			this.values = new HashMap<String, String>(values);
		}
	}

	private static final DeviceKey MAC = DeviceKey.of("Micro MacBook Pro", null, 0);
	private static final DeviceKey USB = DeviceKey.of("USB Audio #2: A.B=C", 1, 0);

	private static RecognitionProfile searched(DeviceKey key, double accept) {
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		values.put(RecognitionParameters.NOTE_ACCEPT, accept);
		values.put(RecognitionParameters.CHORD_MIN_PARTIALS, 3.0);
		values.put(RecognitionParameters.INPUT_LEVEL_MIN, -45.0);
		return new RecognitionProfile(key, 48000, 1760000000123L, values, new RecognitionRate(9, 10, 2, 3), new RecognitionRate(7, 10, 1, 3),
				new FrontEndState(1.5, true), RecognitionProfile.Origin.SEARCHED);
	}

	private static double accept() {
		return RecognitionParameters.NOTE_ACCEPT.getMinimum();
	}

	@Test
	public void twoProfilesComeBackEqualAfterARestart() {
		Memory file = new Memory();
		ProfileLibrary library = new ProfileLibrary(file);
		RecognitionProfile mac = searched(MAC, accept());
		RecognitionProfile usb = RecognitionProfile.defaultChosen(USB, 44100, 1760000000999L, new FrontEndState(1.0, false));
		assertTrue(library.save(mac));
		assertTrue(library.save(usb));
		ProfileLibrary restarted = new ProfileLibrary(file);
		assertEquals(mac, restarted.get(MAC));
		assertEquals(usb, restarted.get(USB));
		assertEquals(2, restarted.keys().size());
		assertNull(restarted.get(DeviceKey.of("Other", null, 0)), "a device that was never calibrated has no profile");
	}

	@Test
	public void theLibraryHasItsOwnFileAndNoThingIsWrittenWhileItIsOnlyRead() {
		assertNotEquals(RecognitionSettingsStore.MODULE, ProfileLibrary.MODULE, "not the recognition store's file, which rewrites itself as a whole");
		Memory file = new Memory();
		ProfileLibrary library = new ProfileLibrary(file);
		library.get(MAC);
		library.keys();
		assertEquals(0, file.writes);
	}

	@Test
	public void anUnreadableOrMissingFileGivesAnEmptyLibraryWithoutAnError() {
		Memory file = new Memory();
		file.failRead = true;
		ProfileLibrary library = new ProfileLibrary(file);
		assertTrue(library.keys().isEmpty());
		assertNull(library.get(MAC));
	}

	@Test
	public void aCorruptOrForeignEntryIsSkippedAndTheOthersStayAndItIsKeptInTheFile() {
		Memory file = new Memory();
		ProfileLibrary library = new ProfileLibrary(file);
		library.save(searched(MAC, accept()));
		library.save(searched(USB, accept()));
		String usbPrefix = "profile." + USB.storageId() + ".";
		// the USB entry becomes corrupt: a number that is not one
		file.values.put(usbPrefix + "sampleRate", "not a number");
		// a profile of a later layout and a key of a later version of the application
		String futureId = DeviceKey.of("Future Mic", null, 0).storageId();
		file.values.put("profile." + futureId + ".layout", "99");
		file.values.put("profile." + futureId + ".sampleRate", "48000");
		file.values.put("something.else", "kept");
		ProfileLibrary restarted = new ProfileLibrary(file);
		assertNotNull(restarted.get(MAC), "the good profile is still there");
		assertNull(restarted.get(USB), "the corrupt one is read as no profile (orange)");
		assertNull(restarted.get(DeviceKey.of("Future Mic", null, 0)), "a later layout is read as no profile");
		assertTrue(restarted.getSkippedIds().contains(USB.storageId()));
		assertTrue(restarted.getSkippedIds().contains(futureId));
		// saving another profile does not destroy what this version cannot read
		assertTrue(restarted.save(searched(DeviceKey.of("Third", null, 0), accept())));
		assertEquals("99", file.values.get("profile." + futureId + ".layout"));
		assertEquals("kept", file.values.get("something.else"));
	}

	@Test
	public void aValueOutsideItsBoundsMakesTheProfileUnreadable() {
		Memory file = new Memory();
		ProfileLibrary library = new ProfileLibrary(file);
		library.save(searched(MAC, accept()));
		file.values.put("profile." + MAC.storageId() + ".value." + RecognitionParameters.NOTE_ACCEPT.getKey(), "99999");
		assertNull(new ProfileLibrary(file).get(MAC));
		file.values.put("profile." + MAC.storageId() + ".value." + RecognitionParameters.NOTE_ACCEPT.getKey(), "12");
		file.values.put("profile." + MAC.storageId() + ".value.recognition.engine.pitch", "yin");
		assertNull(new ProfileLibrary(file).get(MAC), "a value that is not a profile parameter");
	}

	@Test
	public void aFailedWriteLeavesTheOldProfileInTheLibraryAndInTheFile() {
		Memory file = new Memory();
		ProfileLibrary library = new ProfileLibrary(file);
		RecognitionProfile old = searched(MAC, accept());
		assertTrue(library.save(old));
		file.failWrite = true;
		assertFalse(library.save(searched(MAC, RecognitionParameters.NOTE_ACCEPT.getMaximum())), "the save reports its failure");
		assertEquals(old, library.get(MAC), "the library in memory is unchanged");
		file.failWrite = false;
		assertEquals(old, new ProfileLibrary(file).get(MAC), "and so is the file");
		file.failWrite = true;
		assertFalse(library.remove(MAC));
		assertEquals(old, library.get(MAC));
	}

	@Test
	public void removeDeletesOnlyThatDevicesProfileAndItStaysDeletedAfterARestart() {
		Memory file = new Memory();
		ProfileLibrary library = new ProfileLibrary(file);
		library.save(searched(MAC, accept()));
		library.save(searched(USB, accept()));
		assertTrue(library.remove(MAC));
		assertNull(library.get(MAC));
		assertNotNull(library.get(USB));
		ProfileLibrary restarted = new ProfileLibrary(file);
		assertNull(restarted.get(MAC));
		assertNotNull(restarted.get(USB));
		for (String key : file.values.keySet()) {
			assertFalse(key.startsWith("profile." + MAC.storageId() + "."), "no key of the removed profile is left: " + key);
		}
		assertTrue(library.remove(MAC), "removing a device that has no profile is not an error");
	}

	@Test
	public void theSharedInstanceIsOnePerContextAndOnlyReadsTheApplicationFile() {
		TGContext context = new TGContext();
		assertSame(ProfileLibrary.getInstance(context), ProfileLibrary.getInstance(context));
	}

	@Test
	public void listenersAreToldAfterASuccessfulSaveOrRemoveOnly() {
		Memory file = new Memory();
		ProfileLibrary library = new ProfileLibrary(file);
		int[] told = { 0 };
		library.addListener(() -> told[0]++);
		assertTrue(library.save(searched(MAC, accept())));
		assertEquals(1, told[0]);
		file.failWrite = true;
		assertFalse(library.save(searched(USB, accept())));
		assertEquals(1, told[0], "a failed write changes nothing: nobody is told");
		file.failWrite = false;
		assertTrue(library.remove(DeviceKey.of("Never calibrated", null, 0)));
		assertEquals(1, told[0], "nothing to remove: nobody is told");
		assertTrue(library.remove(MAC));
		assertEquals(2, told[0]);
	}
}
