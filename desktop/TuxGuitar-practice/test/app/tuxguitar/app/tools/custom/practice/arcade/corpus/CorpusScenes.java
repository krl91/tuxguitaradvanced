package app.tuxguitar.app.tools.custom.practice.arcade.corpus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeRenderEvent;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeScene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.CameraTrack;
import app.tuxguitar.app.tools.custom.practice.arcade.view.IsometricProjection;
import app.tuxguitar.app.tools.custom.practice.arcade.view.NeckModel;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.RenderState;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxes;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ScreenPoint;
import app.tuxguitar.app.tools.custom.practice.arcade.view.StringColours;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;

/**
 * The scenes of one real track at representative instants, and the structural checks they must satisfy (spec 003 FR-060 to FR-065, SC-017 to SC-023, T062):
 * built from the events the application builds for the track, never from the notes of a beat. A failure is reported with the anonymous identifier of the case.
 */
public final class CorpusScenes {

	public static final Viewport SCREEN = new Viewport(1280, 720);

	/** The five states of an event that are looked at, as shares of the look-ahead before its onset, or after it. */
	public enum Moment {
		SPAWN, READABLE, ARMED, IMPACT, FEEDBACK
	}

	/** One track ready to be drawn. */
	public static final class Prepared {
		public final String id;
		public final List<ArcadeRenderEvent> events;
		public final NeckModel neck;
		public final StringColours colours;
		public final CameraTrack camera;
		public final long timelineDigest;
		public final int anchorBusiest;
		public final int anchorChordRun;
		public final int strings;

		Prepared(String id, List<ArcadeRenderEvent> events, NeckModel neck, StringColours colours, CameraTrack camera, long digest, int busiest, int chordRun, int strings) {
			this.id = id;
			this.events = events;
			this.neck = neck;
			this.colours = colours;
			this.camera = camera;
			this.timelineDigest = digest;
			this.anchorBusiest = busiest;
			this.anchorChordRun = chordRun;
			this.strings = strings;
		}

		public ArcadeScene scene(boolean monochrome) {
			return new ArcadeScene(this.events, this.neck, this.colours, this.camera, SceneFixture.ENGLISH, monochrome);
		}
	}

	private CorpusScenes() {
	}

	static long digestOf(List<ArcadeRenderEvent> events) {
		long digest = 17;
		for (ArcadeRenderEvent event : events) {
			digest = digest * 31 + event.onsetNanos();
			digest = digest * 31 + event.positions().size();
			digest = digest * 31 + event.kind().ordinal();
		}
		return digest;
	}

	/** Reads the file again and builds the events, the neck and the camera of the track of the case. */
	public static Prepared prepare(CorpusReader reader, Path root, CorpusPipeline.Case c) {
		CorpusReader.Result read = reader.read(root.resolve(c.file.relative));
		if (read.song == null) {
			throw new IllegalStateException("the file of case " + c.id + " could not be read again");
		}
		TGSong song = read.song;
		new TGSongManager().updatePreciseStart(song);
		TGTrack track = null;
		for (java.util.Iterator<TGTrack> tracks = song.getTracks(); tracks.hasNext();) {
			TGTrack candidate = tracks.next();
			if (candidate.getNumber() == c.metrics.trackNumber) {
				track = candidate;
			}
		}
		TrackTuning tuning = TrackTuning.ofAnyStrings(track);
		PracticeSequence sequence = PracticeSequenceBuilder.build(song, track, tuning);
		FollowTimeline timeline = FollowTimeline.build(song, sequence, 100);
		List<PracticeEvent> practice = sequence.getEvents();
		List<ArcadeRenderEvent> events = new ArrayList<ArcadeRenderEvent>();
		int maxFret = 0;
		for (int i = 0; i < practice.size(); i++) {
			ArcadeRenderEvent event = ArcadeRenderEvent.of(i, practice.get(i), timeline);
			events.add(event);
			maxFret = Math.max(maxFret, event.maxFret());
		}
		NeckModel neck = NeckModel.of(tuning, Math.max(24, maxFret + 1));
		CameraTrack camera = CameraTrack.build(events, neck, CameraTrack.Settings.standard());
		// the anchors: the start of the busiest second, and the start of the longest run of chords
		int busiest = 0;
		int from = 0;
		int peak = 0;
		for (int i = 0; i < events.size(); i++) {
			while (events.get(i).onsetNanos() - events.get(from).onsetNanos() >= 1_000_000_000L) {
				from++;
			}
			if (i - from + 1 > peak) {
				peak = i - from + 1;
				busiest = from;
			}
		}
		int chordRun = 0;
		int bestRun = 0;
		int run = 0;
		for (int i = 0; i < events.size(); i++) {
			if (events.get(i).kind() == ArcadeRenderEvent.Kind.CHORD) {
				run++;
				if (run > bestRun) {
					bestRun = run;
					chordRun = i - run + 1;
				}
			} else {
				run = 0;
			}
		}
		return new Prepared(c.id, events, neck, StringColours.forStrings(tuning.getStringCount()), camera, digestOf(events), busiest, chordRun, tuning.getStringCount());
	}

	/** The instant and the run of a moment of the event {@code anchor}: before it every event is completed, so that it is the active one. */
	public static long nowOf(Prepared p, int anchor, Moment moment) {
		long look = p.camera.settings().lookAheadNanos;
		long onset = p.events.get(anchor).onsetNanos();
		switch (moment) {
			case SPAWN:
				return onset - (long) (look * 0.97);
			case READABLE:
				return onset - (long) (look * 0.55);
			case ARMED:
				return onset - (long) (look * 0.15);
			case IMPACT:
				return onset + 10_000_000L;
			default:
				return onset + 300_000_000L;
		}
	}

	public static ArcadeRun.View runOf(Prepared p, int anchor, Moment moment) {
		int completed = moment == Moment.FEEDBACK ? anchor + 1 : anchor;
		List<ArcadeRun.EventState> states = new ArrayList<ArcadeRun.EventState>();
		for (int i = 0; i < p.events.size(); i++) {
			states.add(new ArcadeRun.EventState(i, i < completed ? ArcadeVerdict.ON_TIME : ArcadeVerdict.WAITING, 0L, 0, 0, java.util.Collections.emptyList(), new int[0]));
		}
		return new ArcadeRun.View(states, 0, 0, 0, 0, completed);
	}

	/** The index of the active event expected at the instant: the earliest playable event on the screen with no verdict. */
	static int expectedActive(Prepared p, long now, ArcadeRun.View run) {
		CameraTrack.Settings settings = p.camera.settings();
		for (int i = 0; i < p.events.size(); i++) {
			ArcadeRenderEvent event = p.events.get(i);
			RenderState state = RenderState.of(event.onsetNanos(), now, settings.lookAheadNanos, settings.holdAfterNanos);
			if (state == RenderState.HIDDEN || state == RenderState.EXPIRED) {
				continue;
			}
			if (event.kind() != ArcadeRenderEvent.Kind.NOTE && event.kind() != ArcadeRenderEvent.Kind.CHORD) {
				continue;
			}
			if (run.states().get(i).verdict() == ArcadeVerdict.WAITING) {
				return i;
			}
		}
		return -1;
	}

	/** Every failure of the structural rules for a scene, as short texts without any path; empty when the scene is right. */
	public static List<String> check(Prepared p, Scene scene, long now, ArcadeRun.View run, QualityTier tier, String where, boolean mono) {
		List<String> failures = new ArrayList<String>();
		int active = expectedActive(p, now, run);
		// FR-060, FR-065: one event owns the targets, all of its positions and only them, opaque, of the right size, on the right intersections
		Set<Integer> owners = new TreeSet<Integer>();
		List<SceneItem> disks = scene.withRole(Role.TARGET_DISK);
		for (SceneItem item : scene.items()) {
			if (item.role() == Role.TARGET_DISK || item.role() == Role.TARGET_OUTLINE || item.role() == Role.TARGET_HALO) {
				owners.add(item.eventIndex());
			}
		}
		if (active < 0) {
			if (!owners.isEmpty()) {
				failures.add(where + ": neck targets without an active event " + owners);
			}
		} else {
			if (!owners.equals(Set.of(active))) {
				failures.add(where + ": the neck targets belong to " + owners + ", the active event is " + active);
			}
			ArcadeRenderEvent event = p.events.get(active);
			if (disks.size() != event.positions().size()) {
				failures.add(where + ": " + disks.size() + " disks for " + event.positions().size() + " written positions");
			}
			IsometricProjection projection = new IsometricProjection(SCREEN);
			int count = p.neck.layout().count();
			double spacing = count > 1 ? Math.abs(projection.stringY(1, count) - projection.stringY(0, count)) : 40.0;
			double diameter = Math.max(24.0, Math.min(42.0, 0.6 * spacing));
			for (ArcadeRenderEvent.Position position : event.positions()) {
				ScreenPoint at = projection.neck(p.neck.fretCentreX(position.fret()), p.neck.layout().rowOf(position.tgString()), count, p.camera.at(now));
				boolean found = false;
				for (SceneItem disk : disks) {
					if (Math.abs(disk.value(0) - at.x()) < 1e-6 && Math.abs(disk.value(1) - at.y()) < 1e-6 && disk.tgString() == position.tgString()) {
						found = true;
						if (disk.alpha() != 1.0 || Math.abs(2.0 * disk.value(2) - diameter) > 1e-6 || (!mono && disk.colour() != p.colours.of(position.tgString()))) {
							failures.add(where + ": a target disk is not opaque, of the rule's size and of its string's colour");
						}
					}
				}
				if (!found) {
					failures.add(where + ": no disk at string " + position.tgString() + " fret " + position.fret());
				}
			}
		}
		// FR-061: the name of the expanded chord, the generic word otherwise, nothing on a note
		for (SceneItem name : scene.withRole(Role.CARTOUCHE_NAME)) {
			ArcadeRenderEvent event = p.events.get(name.eventIndex());
			String expected = event.kind() != ArcadeRenderEvent.Kind.CHORD ? null : event.chordName() != null ? event.chordName() : "Chord";
			if (expected == null || !expected.equals(name.text())) {
				failures.add(where + ": chord name \"" + name.text() + "\" for an event of kind " + event.kind());
			}
		}
		for (SceneItem frame : scene.withRole(Role.CARTOUCHE_FRAME)) {
			if (p.events.get(frame.eventIndex()).kind() != ArcadeRenderEvent.Kind.CHORD) {
				failures.add(where + ": a chord diagram on a note");
			}
		}
		// FR-062, FR-063: at most four detailed, one diagram, no overlap, nothing cut, the bars on the axis and ordered by depth
		if (SceneBoxes.detailed(scene).size() > ArcadeScene.MAX_DETAILED) {
			failures.add(where + ": " + SceneBoxes.detailed(scene).size() + " detailed events");
		}
		if (scene.withRole(Role.CARTOUCHE_FRAME).size() > 1) {
			failures.add(where + ": " + scene.withRole(Role.CARTOUCHE_FRAME).size() + " chord diagrams");
		}
		String overlap = SceneBoxes.firstOverlap(SceneBoxes.labels(scene));
		if (overlap != null) {
			failures.add(where + ": " + overlap);
		}
		for (SceneBoxes.Box box : SceneBoxes.labels(scene)) {
			if (box.x1 < 0 || box.y1 < 0 || box.x2 > SCREEN.width() || box.y2 > SCREEN.height()) {
				failures.add(where + ": cut label " + box);
			}
		}
		double previousY = Double.MAX_VALUE;
		for (int i = 0; i < p.events.size(); i++) {
			List<SceneItem> bars = scene.forEvent(i, Role.TIMING_BAR);
			bars = bars.isEmpty() ? scene.forEvent(i, Role.PREVIEW_BAR) : bars;
			if (bars.isEmpty()) {
				continue;
			}
			SceneBoxes.Box box = SceneBoxes.ofQuad(bars.get(0));
			if (Math.abs((box.x1 + box.x2) / 2.0 - SCREEN.width() * ArcadeScene.CHORD_AXIS) > 1e-6) {
				failures.add(where + ": the bar of event " + i + " is off the axis");
			}
			if (p.events.get(i).onsetNanos() > now) {
				if (box.y2 >= previousY) {
					failures.add(where + ": the bars are not in depth order at event " + i);
				}
				previousY = box.y2;
			}
		}
		if (digestOf(p.events) != p.timelineDigest) {
			failures.add(where + ": the timeline changed");
		}
		return failures;
	}

	/** Builds and checks every representative scene of a track: two anchors, five moments, FULL and PERFORMANCE. Returns the failures and counts the scenes. */
	public static List<String> checkAll(Prepared p, int[] sceneCount) {
		List<String> failures = new ArrayList<String>();
		int[] anchors = p.anchorChordRun == p.anchorBusiest ? new int[] { p.anchorBusiest } : new int[] { p.anchorBusiest, p.anchorChordRun };
		for (int anchor : anchors) {
			for (Moment moment : Moment.values()) {
				long now = nowOf(p, anchor, moment);
				ArcadeRun.View run = runOf(p, anchor, moment);
				for (QualityTier tier : new QualityTier[] { QualityTier.FULL, QualityTier.PERFORMANCE }) {
					for (boolean mono : new boolean[] { false, true }) {
						Scene scene = p.scene(mono).build(now, SCREEN, run, 100, tier);
						sceneCount[0]++;
						failures.addAll(check(p, scene, now, run, tier, p.id + " " + moment + " " + tier + (mono ? " mono" : "") + " at event " + anchor, mono));
					}
				}
			}
		}
		return failures;
	}

	static String format(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
