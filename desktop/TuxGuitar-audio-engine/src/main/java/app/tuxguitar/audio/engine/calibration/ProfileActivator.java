package app.tuxguitar.audio.engine.calibration;

import java.util.List;
import java.util.function.Supplier;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.util.TGContext;

/**
 * Lays the profile of the selected device over the recognition settings (spec 013, FR-007, FR-010). It lives with the engine, not with a window: the
 * preparation window exists only while it is open, but the tuner and every practice mode read the same settings. It listens to the selection (and so applies
 * the input remembered at start), to the library (a calibration that just ended, a profile forgotten) and to the engine's sample rate, which it receives as a
 * {@link HopListener}'s {@code configure}.
 *
 * <p>The selection notifies each listener on a thread of its own, so two quick selections may arrive out of order: the activator therefore ignores what the
 * event says and reads the LATEST selection, and applies without a lock (a lock held while the store tells the engine to rebuild could meet a lock the
 * analysis thread holds), checking after applying that the selection did not move meanwhile.
 */
public final class ProfileActivator implements HopListener {

	private static final String CONTEXT_KEY = ProfileActivator.class.getName();

	private final CurrentInputSelection selection;
	private final ProfileLibrary library;
	private final RecognitionSettingsStore store;
	private final Supplier<List<AudioInputDescriptor>> catalog;
	private volatile int sampleRate;

	public ProfileActivator(CurrentInputSelection selection, ProfileLibrary library, RecognitionSettingsStore store, Supplier<List<AudioInputDescriptor>> catalog) {
		this.selection = selection;
		this.library = library;
		this.store = store;
		this.catalog = catalog;
	}

	/** Starts listening and applies the profile of what is selected now. */
	public void install() {
		this.selection.addListener(this::selectionChanged);
		this.library.addListener(this::refresh);
		refresh();
	}

	/** The activator of an engine: wired to the application's library and settings; made when the engine is. */
	public static ProfileActivator attach(TGContext context, AudioEngine engine) {
		ProfileActivator activator = new ProfileActivator(engine.getSelection(), ProfileLibrary.getInstance(context), RecognitionSettingsStore.getInstance(context),
				() -> engine.getCatalog().listInputs());
		activator.install();
		engine.addHopListener(activator);
		context.setAttribute(CONTEXT_KEY, activator);
		return activator;
	}

	/** The activator attached to the engine of this context, or null when none was. */
	public static ProfileActivator of(TGContext context) {
		return context.hasAttribute(CONTEXT_KEY) ? (ProfileActivator) context.getAttribute(CONTEXT_KEY) : null;
	}

	/** The capture rate the engine announced, or 0 while it is not known (no capture yet). */
	public int getSampleRate() {
		return this.sampleRate;
	}

	/** An event of the selection: what it says is ignored, the latest selection is read. */
	public void selectionChanged(CurrentInputSelection.Snapshot ignored) {
		refresh();
	}

	public void refresh() {
		for (;;) {
			CurrentInputSelection.Snapshot snapshot = this.selection.snapshot();
			this.store.setActiveProfile(profileFor(snapshot.getInput()));
			if (this.selection.snapshot().getRevision() == snapshot.getRevision()) {
				return;
			}
		}
	}

	private RecognitionProfile profileFor(AudioInputDescriptor input) {
		if (input == null) {
			return null;
		}
		RecognitionProfile profile = this.library.get(DeviceKey.of(input, this.catalog.get()));
		return profile != null && profile.appliesAt(this.sampleRate) ? profile : null;
	}

	@Override
	public void configure(int rate) {
		if (rate != this.sampleRate) {
			this.sampleRate = rate;
			refresh();
		}
	}

	@Override
	public void hop(float[] samples, int offset, int length) {
		// nothing: only the rate announced by configure matters
	}
}
