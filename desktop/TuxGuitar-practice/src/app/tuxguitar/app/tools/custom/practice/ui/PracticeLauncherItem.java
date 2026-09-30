package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.view.toolbar.main.TGMainToolBarItemUpdater;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.util.TGContext;

/**
 * State of the transport-bar Practice button: checked while the practice window is open, disabled
 * with an explanatory tooltip when there is nothing to practise ({@link LauncherState}).
 */
public final class PracticeLauncherItem extends TGMainToolBarItemUpdater {

	/** The check for a practicable track is repeated at most this often: the tool bar updates on every cursor move. */
	private static final long TRACK_CHECK_INTERVAL_NANOS = 1_000_000_000L;

	private final String itemName;
	private final java.util.function.BooleanSupplier windowOpen;
	private TGSong checkedSong;
	private boolean checkedSongHasTrack;
	private long checkedAtNanos;

	public PracticeLauncherItem(String itemName, java.util.function.BooleanSupplier windowOpen) {
		this.itemName = itemName;
		this.windowOpen = windowOpen;
	}

	private synchronized LauncherState state() {
		boolean open = this.windowOpen.getAsBoolean();
		TGSong song = TuxGuitar.getInstance().getDocumentManager().getSong();
		boolean hasTrack = false;
		if( !open && song != null ) {
			long now = System.nanoTime();
			if( song != this.checkedSong || now - this.checkedAtNanos > TRACK_CHECK_INTERVAL_NANOS ) {
				this.checkedSong = song;
				this.checkedSongHasTrack = !PracticeTrackCandidates.evaluate(song, null).getCandidates().isEmpty();
				this.checkedAtNanos = now;
			}
			hasTrack = this.checkedSongHasTrack;
		}
		return LauncherState.of(open, song != null, hasTrack);
	}

	@Override
	public boolean enabled(TGContext context, boolean isRunning) {
		return state().isEnabled();
	}

	@Override
	public boolean checked(TGContext context, boolean isRunning) {
		return state().isChecked();
	}

	@Override
	public String getText(TGContext context, boolean isRunning) {
		return state().tooltipKey(this.itemName);
	}
}
