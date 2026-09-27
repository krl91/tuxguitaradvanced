package app.tuxguitar.audio.engine.session;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * What the practice and tuner plugins share, per {@code TGContext}, without depending on each
 * other. UI-free; in memory for the application run only; never written to the song or settings.
 *
 * <ul>
 * <li>Guitar-confirmed tracks (FR-003, FR-052): tracks the learner accepted as Practice Track in
 * this run; a song document is identified by its {@link TGSong} instance.</li>
 * <li>The current Practice Track while practice is being prepared or running (FR-052 a).</li>
 * <li>Whether the tuner window is open (FR-058), and the tuning the learner explicitly chose in
 * the tuner, if any (FR-062).</li>
 * </ul>
 * Listeners are notified on the thread that made the change and must return quickly.
 */
public final class PracticeTunerContext {

	public interface Listener {
		void practiceTunerContextChanged(PracticeTunerContext context);
	}

	private final Map<TGSong, Set<TGTrack>> confirmed = new WeakHashMap<TGSong, Set<TGTrack>>();
	private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
	private volatile TGTrack practiceTrack;
	private volatile boolean tunerOpen;
	private volatile int[] tunerChosenTargets;

	PracticeTunerContext() {
	}

	public static PracticeTunerContext getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, PracticeTunerContext.class.getName(), new TGSingletonFactory<PracticeTunerContext>() {
			@Override
			public PracticeTunerContext createInstance(TGContext singletonContext) {
				return new PracticeTunerContext();
			}
		});
	}

	// ---------------------------------------------------------------- guitar-confirmed tracks

	public void confirm(TGSong song, TGTrack track) {
		synchronized (this.confirmed) {
			this.confirmed.computeIfAbsent(song, key -> Collections.newSetFromMap(new WeakHashMap<TGTrack, Boolean>())).add(track);
		}
		fire();
	}

	public boolean isConfirmed(TGSong song, TGTrack track) {
		synchronized (this.confirmed) {
			Set<TGTrack> tracks = this.confirmed.get(song);
			return (tracks != null && tracks.contains(track));
		}
	}

	// ---------------------------------------------------------------- practice track

	/** The Practice Track while practice is prepared or running; null otherwise. */
	public void setPracticeTrack(TGTrack track) {
		if( this.practiceTrack != track ) {
			this.practiceTrack = track;
			fire();
		}
	}

	public TGTrack getPracticeTrack() {
		return this.practiceTrack;
	}

	// ---------------------------------------------------------------- tuner

	public void setTunerOpen(boolean open) {
		if( this.tunerOpen != open ) {
			this.tunerOpen = open;
			fire();
		}
	}

	public boolean isTunerOpen() {
		return this.tunerOpen;
	}

	/** Targets (string 1 first) of the tuning the learner explicitly chose in the tuner, or null. */
	public void setTunerChosenTargets(int[] targets) {
		this.tunerChosenTargets = (targets != null ? targets.clone() : null);
		fire();
	}

	public int[] getTunerChosenTargets() {
		int[] targets = this.tunerChosenTargets;
		return (targets != null ? targets.clone() : null);
	}

	// ---------------------------------------------------------------- listeners

	public void addListener(Listener listener) {
		this.listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		this.listeners.remove(listener);
	}

	private void fire() {
		for( Listener listener : this.listeners ) {
			try {
				listener.practiceTunerContextChanged(this);
			} catch (RuntimeException exception) {
				// One consumer must not prevent delivery to the others.
			}
		}
	}
}
