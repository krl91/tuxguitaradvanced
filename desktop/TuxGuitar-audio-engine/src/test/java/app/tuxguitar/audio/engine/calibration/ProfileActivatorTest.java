package app.tuxguitar.audio.engine.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * Spec 013 (T038): the profile of the selected device is laid over the settings by something that lives with the engine, not with a window (FR-008,
 * FR-010, SC-002). The selection notifies each listener on a thread of its own, possibly out of order, so the activator ignores what the event says and
 * reads the latest selection (contract profile-library, invariant 10).
 */
public class ProfileActivatorTest {

	private static final double MAC_ACCEPT = RecognitionParameters.NOTE_ACCEPT.getMinimum();
	private static final double USB_ACCEPT = RecognitionParameters.NOTE_ACCEPT.getMaximum();

	private static final class Memory implements ProfilePersistence {
		Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	private static final class StoreMemory implements RecognitionSettingsStore.Persistence {
		Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	private TGContext context;
	private CurrentInputSelection selection;
	private ProfileLibrary library;
	private RecognitionSettingsStore store;
	private List<AudioInputDescriptor> catalog;
	private ProfileActivator activator;

	private static AudioInputDescriptor input(int mixerIndex, String name) {
		return new AudioInputDescriptor(mixerIndex + ":" + name + ":mono", name, null, 1, name, AudioInputDescriptor.Kind.UNKNOWN, false);
	}

	private static RecognitionProfile profile(DeviceKey key, int rate, double accept, RecognitionProfile.Origin origin) {
		if (origin == RecognitionProfile.Origin.DEFAULT_CHOSEN) {
			return RecognitionProfile.defaultChosen(key, rate, 1L, new FrontEndState(1.0, false));
		}
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		values.put(RecognitionParameters.NOTE_ACCEPT, accept);
		return new RecognitionProfile(key, rate, 1L, values, new RecognitionRate(9, 10, 2, 3), new RecognitionRate(7, 10, 1, 3), new FrontEndState(1.0, false), origin);
	}

	@BeforeEach
	public void setUp() {
		this.context = new TGContext();
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
		this.selection = CurrentInputSelection.getInstance(this.context);
		this.library = new ProfileLibrary(new Memory());
		this.store = new RecognitionSettingsStore(new StoreMemory());
		this.catalog = new ArrayList<AudioInputDescriptor>(Arrays.asList(input(0, "Micro MacBook Pro"), input(1, "USB Audio"), input(2, "USB Audio")));
	}

	@AfterEach
	public void tearDown() {
		TGThreadManager.getInstance(this.context).dispose();
	}

	private ProfileActivator install() {
		this.activator = new ProfileActivator(this.selection, this.library, this.store, () -> this.catalog);
		this.activator.install();
		return this.activator;
	}

	private DeviceKey key(int catalogIndex) {
		return DeviceKey.of(this.catalog.get(catalogIndex), this.catalog);
	}

	private double accept() {
		return this.store.get().get(RecognitionParameters.NOTE_ACCEPT);
	}

	private static void waitFor(BooleanSupplier condition, String what) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10000;
		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
			Thread.sleep(10);
		}
		assertTrue(condition.getAsBoolean(), what);
	}

	@Test
	public void theProfileOfTheSelectedDeviceIsLaidOverTheSettingsWithNoWindowOpen() throws InterruptedException {
		this.library.save(profile(key(0), 48000, MAC_ACCEPT, RecognitionProfile.Origin.SEARCHED));
		install();
		assertEquals(this.store.getDefault(), this.store.get(), "nothing selected yet: the default");
		this.selection.select(this.catalog.get(0), null);
		waitFor(() -> accept() == MAC_ACCEPT, "the selection activates the profile of the device");
		assertNotEquals(this.store.getDefault(), this.store.get());
		this.selection.select(this.catalog.get(1), null);
		waitFor(() -> accept() == this.store.getDefault().get(RecognitionParameters.NOTE_ACCEPT), "a device with no profile uses the default again");
		this.selection.clear();
		waitFor(() -> this.store.get().equals(this.store.getDefault()), "no input: the default");
	}

	@Test
	public void theInputAlreadySelectedAtStartIsAppliedWhenTheActivatorIsMade() {
		this.library.save(profile(key(0), 48000, MAC_ACCEPT, RecognitionProfile.Origin.SEARCHED));
		this.selection.select(this.catalog.get(0), null);
		install();
		assertEquals(MAC_ACCEPT, accept(), "the remembered input was selected before any window: the first session must not run on the default");
	}

	@Test
	public void aStaleEventDoesNotPutBackTheProfileOfAnEarlierSelection() throws InterruptedException {
		this.library.save(profile(key(0), 48000, MAC_ACCEPT, RecognitionProfile.Origin.SEARCHED));
		this.library.save(profile(key(1), 48000, USB_ACCEPT, RecognitionProfile.Origin.SEARCHED));
		List<CurrentInputSelection.Snapshot> delivered = new ArrayList<CurrentInputSelection.Snapshot>();
		this.selection.addListener(delivered::add);
		install();
		this.selection.select(this.catalog.get(0), null);
		waitFor(() -> delivered.size() >= 1 && accept() == MAC_ACCEPT, "A is active");
		CurrentInputSelection.Snapshot staleA = delivered.get(0);
		this.selection.select(this.catalog.get(1), null);
		waitFor(() -> accept() == USB_ACCEPT, "B is active");
		// the event of A arrives late, after the selection moved on to B
		this.activator.selectionChanged(staleA);
		assertEquals(USB_ACCEPT, accept(), "the latest selection wins, whatever order the events arrive in");
		// and A, B, A in a quick row ends with A
		this.selection.select(this.catalog.get(0), null);
		waitFor(() -> accept() == MAC_ACCEPT, "A again");
		CurrentInputSelection.Snapshot latest = delivered.get(delivered.size() - 1);
		this.activator.selectionChanged(delivered.get(1));
		this.activator.selectionChanged(latest);
		this.activator.selectionChanged(delivered.get(0));
		assertEquals(MAC_ACCEPT, accept());
	}

	@Test
	public void aProfileMadeAtAnotherSampleRateIsNotAppliedUntilTheRateMatches() {
		this.library.save(profile(key(0), 48000, MAC_ACCEPT, RecognitionProfile.Origin.SEARCHED));
		this.selection.select(this.catalog.get(0), null);
		install();
		assertEquals(MAC_ACCEPT, accept(), "the capture rate is not known yet: the profile counts");
		this.activator.configure(44100);
		assertEquals(this.store.getDefault(), this.store.get(), "a profile made at 48 kHz is not applied at 44.1 kHz");
		this.activator.configure(48000);
		assertEquals(MAC_ACCEPT, accept());
	}

	@Test
	public void aProfileSavedForTheSelectedDeviceIsAppliedAtOnceAndForgettingItRestoresTheDefault() {
		this.selection.select(this.catalog.get(0), null);
		install();
		assertEquals(this.store.getDefault(), this.store.get());
		this.library.save(profile(key(0), 48000, MAC_ACCEPT, RecognitionProfile.Origin.SEARCHED));
		assertEquals(MAC_ACCEPT, accept(), "a calibration just finished: the settings change without selecting again");
		this.library.remove(key(0));
		assertEquals(this.store.getDefault(), this.store.get(), "forgotten: the default");
	}

	@Test
	public void theDefaultChosenLeavesTheDefaultInForceWhateverItIsEditedTo() {
		this.selection.select(this.catalog.get(0), null);
		install();
		this.library.save(profile(key(0), 48000, 0, RecognitionProfile.Origin.DEFAULT_CHOSEN));
		this.store.update(this.store.getDefault().with(RecognitionParameters.NOTE_ACCEPT, USB_ACCEPT));
		assertEquals(USB_ACCEPT, accept(), "the player edited the default configuration: a device left on the default follows it");
	}

	@Test
	public void twoIdenticalAdaptersEachHaveTheirOwnProfile() throws InterruptedException {
		this.library.save(profile(key(1), 48000, MAC_ACCEPT, RecognitionProfile.Origin.SEARCHED));
		this.library.save(profile(key(2), 48000, USB_ACCEPT, RecognitionProfile.Origin.SEARCHED));
		assertNotEquals(key(1), key(2));
		install();
		this.selection.select(this.catalog.get(1), null);
		waitFor(() -> accept() == MAC_ACCEPT, "the first adapter");
		this.selection.select(this.catalog.get(2), null);
		waitFor(() -> accept() == USB_ACCEPT, "the second adapter");
	}

	@Test
	public void changingTheProfileRebuildsTheAnalysisOnceAndTheSameProfileAgainDoesNothing() {
		this.library.save(profile(key(0), 48000, MAC_ACCEPT, RecognitionProfile.Origin.SEARCHED));
		this.selection.select(this.catalog.get(0), null);
		int[] changes = { 0 };
		this.store.addListener((previous, current) -> changes[0]++);
		install();
		assertEquals(1, changes[0], "one change of the effective settings");
		this.activator.refresh();
		this.activator.refresh();
		assertEquals(1, changes[0], "the same profile again: nobody is told, the engine does not rebuild");
	}

	@Test
	public void theActivatorAttachedToAnEngineIsFoundByItsContextAndKnowsTheRateOnceItIsAnnounced() {
		app.tuxguitar.audio.engine.AudioEngine engine = app.tuxguitar.audio.engine.FakeAudioInput.newEngine(this.context,
				app.tuxguitar.audio.engine.settings.RecognitionSettings.defaults());
		ProfileActivator attached = ProfileActivator.attach(this.context, engine);
		org.junit.jupiter.api.Assertions.assertSame(attached, ProfileActivator.of(this.context));
		assertEquals(0, attached.getSampleRate(), "no capture yet: the rate is not known");
		attached.configure(44100);
		assertEquals(44100, attached.getSampleRate());
	}
}
