package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import app.tuxguitar.action.TGAction;
import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.action.TGActionManager;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.util.TGContext;

/**
 * The real way to the tuner (spec 010, found by the owner on the built application): the tuner refuses to open while a practice track is set
 * ({@code TunerWindow.open}), and the preparation window sets one as soon as it exists, shown or not. The opener must therefore lift the practice track while the
 * tuner is open and give it back when the tuner closes, whichever way it closes. The tuner here is a fake action with the same rule as the real one.
 */
public class PracticeTunerOpenerTest {

	private final TGContext context = new TGContext();
	private final PracticeTunerContext shared = PracticeTunerContext.getInstance(this.context);
	private final AtomicInteger refused = new AtomicInteger();

	/** The action {@code tuner.tuner} with the rule of {@code TunerWindow.open}: refused while a practice track is set. */
	private void mapTuner() {
		TGActionManager.getInstance(this.context).mapAction(PracticeTunerOpener.ACTION_ID, (TGAction) (TGActionContext actionContext) -> {
			if (this.shared.getPracticeTrack() != null) {
				this.refused.incrementAndGet();
				return;
			}
			this.shared.setTunerOpen(true);
		});
	}

	@Test
	public void theTunerOpensEvenThoughThePreparationWindowHoldsAPracticeTrackAndTheTrackComesBackWhenTheTunerCloses() {
		mapTuner();
		TGTrack track = new TGFactory().newTrack();
		this.shared.setPracticeTrack(track);
		PracticeTunerOpener opener = new PracticeTunerOpener(this.context);
		AtomicInteger closed = new AtomicInteger();
		opener.setClosedListener(closed::incrementAndGet);
		assertTrue(opener.available());
		opener.open();
		assertEquals(0, this.refused.get(), "the tuner was not refused");
		assertTrue(this.shared.isTunerOpen(), "the tuner is open");
		assertEquals(null, this.shared.getPracticeTrack(), "no practice track while the tuner is open");
		this.shared.setTunerOpen(false); // the learner closes the tuner
		assertEquals(1, closed.get(), "the start window is told");
		assertSame(track, this.shared.getPracticeTrack(), "the practice track is given back");
		opener.setClosedListener(null);
	}

	@Test
	public void cancellingWhileTheTunerIsOpenGivesTheTrackBackAndALaterCloseTellsNobody() {
		mapTuner();
		TGTrack track = new TGFactory().newTrack();
		this.shared.setPracticeTrack(track);
		PracticeTunerOpener opener = new PracticeTunerOpener(this.context);
		AtomicInteger closed = new AtomicInteger();
		opener.setClosedListener(closed::incrementAndGet);
		opener.open();
		opener.setClosedListener(null); // the start window ends (cancelled) while the tuner is open
		assertSame(track, this.shared.getPracticeTrack(), "the practice track is given back at once");
		this.shared.setTunerOpen(false);
		assertEquals(0, closed.get());
		assertFalse(this.shared.isTunerOpen());
	}
}
