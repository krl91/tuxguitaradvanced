package app.tuxguitar.app.tools.custom.practice.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * Tracks the learner explicitly accepted as Practice Track during this application run
 * (guitar-confirmed tracks, FR-003/FR-052). In memory only; never written to the song or
 * settings. A song document is identified by its {@link TGSong} instance, so reopening a file
 * may ask again. Entries of closed documents are released with them.
 */
public final class GuitarConfirmationMemory {

	private final Map<TGSong, Set<TGTrack>> confirmed = new WeakHashMap<TGSong, Set<TGTrack>>();

	GuitarConfirmationMemory() {
	}

	public static GuitarConfirmationMemory getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, GuitarConfirmationMemory.class.getName(), new TGSingletonFactory<GuitarConfirmationMemory>() {
			@Override
			public GuitarConfirmationMemory createInstance(TGContext singletonContext) {
				return new GuitarConfirmationMemory();
			}
		});
	}

	public synchronized void confirm(TGSong song, TGTrack track) {
		this.confirmed.computeIfAbsent(song, key -> Collections.newSetFromMap(new WeakHashMap<TGTrack, Boolean>())).add(track);
	}

	public synchronized boolean isConfirmed(TGSong song, TGTrack track) {
		Set<TGTrack> tracks = this.confirmed.get(song);
		return (tracks != null && tracks.contains(track));
	}
}
