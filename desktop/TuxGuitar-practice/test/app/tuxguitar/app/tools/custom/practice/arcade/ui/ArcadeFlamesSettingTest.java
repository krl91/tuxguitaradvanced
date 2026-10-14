package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeScoring;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;

/**
 * The setting of the stage flames reaches the picture of a run (spec 009 FR-008, FR-009, T022): off draws none, discreet fewer and fainter than full, a
 * setting changed while a run goes on is used by the next run and not by that one, and every run starts with embers whatever the run before did. The run is
 * started the way the preparation window does it: the setting is read from the settings at that moment and handed to the source of the frames.
 */
public class ArcadeFlamesSettingTest {

	private static final long FIRST_TICK = 960L;
	private static final long MS = 1_000_000L;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	private final SceneFixture fixture;
	private final FakeTempoClock clock;

	public ArcadeFlamesSettingTest() {
		SongFixture song = new SongFixture(2);
		for (int i = 0; i < 8; i++) {
			song.quarter(i / 4, i % 4, 1 + i % 6, i * 2 + 1);
		}
		this.fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		this.clock = new FakeTempoClock(ticks -> this.fixture.timeline.nanosAtTick((long) ticks), FIRST_TICK, 100_000L);
	}

	private static final class Play implements java.util.function.Supplier<ArcadeRun.View> {
		@Override
		public ArcadeRun.View get() {
			return new ArcadeRun.View(new ArrayList<ArcadeRun.EventState>(), ArcadeScoring.standard().maximum(10), 0, 0, 0, 10);
		}
	}

	/** A run as the window starts it: the setting is read now. */
	private ArcadeFrameSource startRun(FollowSettings settings) {
		this.clock.start(FIRST_TICK);
		this.clock.pause();
		return new ArcadeFrameSource(this.fixture.scene(false), this.fixture.timeline, FIRST_TICK, this.clock, new Play(), () -> 100,
				new QualityController(ArcadeCanvas.FRAME_NANOS), 2000 * MS, 0L, settings.getFlames(), true, ArcadeScoring.standard());
	}

	private Scene play(ArcadeFrameSource source, int pictures) {
		Scene scene = null;
		for (int i = 0; i < pictures; i++) {
			scene = source.frame(this.clock.nowNanos(), SCREEN);
			this.clock.advance(50 * MS);
		}
		return scene;
	}

	private static int tongues(Scene scene) {
		Set<Integer> codes = new HashSet<Integer>();
		for (SceneItem item : scene.withRole(Role.FLAME_TONGUE)) {
			codes.add(item.tgString());
		}
		return codes.size();
	}

	private static double strongest(Scene scene) {
		double alpha = 0.0;
		for (SceneItem item : scene.withRole(Role.FLAME_TONGUE)) {
			alpha = Math.max(alpha, item.alpha());
		}
		return alpha;
	}

	private FollowSettings settings(FlameSetting setting) {
		FollowSettings settings = new FollowSettingsAccess().make();
		settings.setFlames(setting);
		return settings;
	}

	/** The settings of the tests, made without a store as the other tests of the panel do. */
	private static final class FollowSettingsAccess {
		FollowSettings make() {
			try {
				java.lang.reflect.Constructor<FollowSettings> constructor = FollowSettings.class.getDeclaredConstructor();
				constructor.setAccessible(true);
				return constructor.newInstance();
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError(exception);
			}
		}
	}

	@Test
	public void offDrawsNoFlameDiscreetFewerAndFaintersThanFull() {
		Scene off = play(startRun(settings(FlameSetting.OFF)), 40);
		assertTrue(off.withRole(Role.FLAME_TONGUE).isEmpty() && off.withRole(Role.FLAME_EMBER).isEmpty(), "off: nothing");
		Scene discreet = play(startRun(settings(FlameSetting.DISCREET)), 40);
		Scene full = play(startRun(settings(FlameSetting.FULL)), 40);
		assertTrue(tongues(discreet) > 0 && tongues(discreet) < tongues(full), "discreet has fewer tongues: " + tongues(discreet) + " < " + tongues(full));
		assertTrue(strongest(discreet) < strongest(full), "and fainter ones");
	}

	@Test
	public void aSettingChangedDuringARunIsUsedByTheNextRunAndNotByThatOne() {
		FollowSettings settings = settings(FlameSetting.FULL);
		ArcadeFrameSource run = startRun(settings);
		int before = tongues(play(run, 40));
		settings.setFlames(FlameSetting.OFF); // the learner changes it in the panel while the run goes on
		Scene during = play(run, 20);
		assertEquals(before, tongues(during), "the run in progress keeps the flames it started with");
		ArcadeFrameSource next = startRun(settings);
		Scene afterwards = play(next, 40);
		assertTrue(afterwards.withRole(Role.FLAME_TONGUE).isEmpty(), "the next run reads the new setting: off");
		settings.setFlames(FlameSetting.DISCREET);
		assertTrue(tongues(play(startRun(settings), 40)) > 0, "and the one after it, discreet");
	}

	@Test
	public void everyRunStartsWithEmbersOnlyWhateverTheRunBeforeDid() {
		FollowSettings settings = settings(FlameSetting.FULL);
		ArcadeFrameSource first = startRun(settings);
		assertTrue(tongues(play(first, 40)) > 0, "a perfect run ends with full flames");
		ArcadeFrameSource second = startRun(settings);
		Scene opening = second.frame(this.clock.nowNanos(), SCREEN);
		assertEquals(0, tongues(opening), "the next run opens with no tongue: the level starts at zero");
		assertTrue(!opening.withRole(Role.FLAME_EMBER).isEmpty(), "but with embers");
	}
}
