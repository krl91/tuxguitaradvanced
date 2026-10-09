package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Shape;

/**
 * The picture of an Arcade run at one instant (spec 003 FR-025 to FR-030, SC-010 to SC-012, contracts/arcade-view.md): the neck, the notes, the chords
 * with their timing bar and diagram, the verdict badges and the HUD, as a list of plain items in paint order. It is a pure function of the events (built
 * from the score and the timeline), the camera track, the clock reading and the state of the run: no state of its own, nothing accumulated from one
 * frame to the next, so a pause, a restart, a change of speed or a dropped frame cannot make it drift. It never contains the score (FR-021).
 */
public final class ArcadeScene {

	static final int NECK_COLOUR = 0x1B2028;
	static final int WIRE_COLOUR = 0x8A93A0;
	static final int INLAY_COLOUR = 0x59616E;
	static final int HIT_COLOUR = 0xF2F4F7;
	static final int BAR_COLOUR = 0xE6E9ED;
	static final int TEXT_COLOUR = 0xEEF0F3;
	static final int FRAME_COLOUR = 0x12151B;
	static final int DIAGRAM_LINE = 0xB8BEC8;
	static final int HEADER_COLOUR = 0x2F3647;
	static final int HEADER_TEXT_COLOUR = 0xFFFFFF;
	/** The outline of the target on the neck: the colour that stands out most from the neck (white on the dark neck, near black on the light one). */
	static final int OUTLINE_COLOUR = 0xFEFEFE;
	/** The width of the outline of a target, in logical pixels: between two and four (FR-065). */
	public static final double OUTLINE_PX = 3.0;
	/** The filled core of a target: the least and the most it measures, in logical pixels at the density of the display, and its share of the space between strings. */
	public static final double TARGET_MIN_PX = 24.0;
	public static final double TARGET_MAX_PX = 42.0;
	public static final double TARGET_SPACING_SHARE = 0.60;

	private final List<ArcadeRenderEvent> events;
	private final NeckModel neck;
	private final StringColours colours;
	private final CameraTrack camera;
	private final SceneLabels labels;
	private final boolean monochrome;
	private final ChordCartouche[] cartouches;

	public ArcadeScene(List<ArcadeRenderEvent> events, NeckModel neck, StringColours colours, CameraTrack camera, SceneLabels labels, boolean monochrome) {
		this.events = events;
		this.neck = neck;
		this.colours = colours;
		this.camera = camera;
		this.labels = labels;
		this.monochrome = monochrome;
		this.cartouches = new ChordCartouche[events.size()];
		for (int i = 0; i < events.size(); i++) {
			if (events.get(i).kind() == ArcadeRenderEvent.Kind.CHORD) {
				this.cartouches[i] = ChordCartouche.of(events.get(i), neck.layout());
			}
		}
	}

	/** The colour of a verdict badge: separate from the string colours, which it never uses. */
	public static int verdictColour(ArcadeVerdict verdict) {
		switch (verdict) {
			case ON_TIME:
				return 0x2EBD59;
			case EARLY:
			case LATE:
				return 0xF0A020;
			case WRONG:
				return 0xE04848;
			case INCOMPLETE:
				return 0xE0703A;
			case MISSED:
				return 0xB04058;
			case AUTO_PASSED:
				return 0x7F8FA6;
			default:
				return 0x9AA0A6;
		}
	}

	/** The symbol of a verdict: always shown with its word, so that colour is never the only information. */
	public static String verdictSymbol(ArcadeVerdict verdict) {
		switch (verdict) {
			case ON_TIME:
				return "✓";
			case EARLY:
				return "‹";
			case LATE:
				return "›";
			case WRONG:
				return "✗";
			case INCOMPLETE:
				return "◐";
			case MISSED:
				return "○";
			case AUTO_PASSED:
				return "»";
			case NOT_JUDGED:
				return "–";
			default:
				return "…";
		}
	}

	/**
	 * The one axis of all the chord bars (FR-063): the middle of the approach lane, as a share of the width of the view. A chord is placed by its onset and
	 * its depth alone; its frets are used for the neck ghost and the camera, never to put its bar to the left or to the right.
	 */
	public static final double CHORD_AXIS = 0.5;
	/** The half width of a chord bar at the hit line, as a share of the width of the view; it shrinks with the distance as everything does. */
	public static final double CHORD_HALF_WIDTH = 0.26;
	/** The gap kept between two labels, in pixels. */
	static final double GAP = 4.0;

	private boolean densityBudget = true;
	private Theme theme = Theme.DARK;
	private double uiScale = 1.0;
	private double markerSize = 1.0;

	/**
	 * Switches the density budget off, for the measurement of the unreduced cost and for the proof that the budget changes nothing of the judgement
	 * (SC-019). A view for the learner always has it on: the unreduced picture is unreadable (the owner refused it).
	 */
	public ArcadeScene withDensityBudget(boolean enabled) {
		ArcadeScene copy = copy();
		copy.densityBudget = enabled;
		return copy;
	}

	/** The same scene in another theme. */
	public ArcadeScene withTheme(Theme theme) {
		ArcadeScene copy = copy();
		copy.theme = theme;
		return copy;
	}

	/** The same scene with the discs of the incoming notes at another size: 1 is the normal one, 0.5 half of it. The disks on the neck do not change. */
	public ArcadeScene withMarkerSize(double factor) {
		ArcadeScene copy = copy();
		copy.markerSize = Math.max(0.3, Math.min(2.0, factor));
		return copy;
	}

	/** The same scene for a display of another density: 1 is a normal one, 2 a Retina one. The target on the neck grows with it (FR-065). */
	public ArcadeScene withUiScale(double scale) {
		ArcadeScene copy = copy();
		copy.uiScale = scale;
		return copy;
	}

	private ArcadeScene copy() {
		ArcadeScene copy = new ArcadeScene(this.events, this.neck, this.colours, this.camera, this.labels, this.monochrome);
		copy.densityBudget = this.densityBudget;
		copy.theme = this.theme;
		copy.uiScale = this.uiScale;
		copy.markerSize = this.markerSize;
		return copy;
	}

	/**
	 * @param nowNanos the clock reading of the player
	 * @param run the state of the run, or null before the first snapshot
	 * @param speedPercent the speed of the run, for the HUD
	 */
	public Scene build(long nowNanos, Viewport viewport, ArcadeRun.View run, int speedPercent) {
		return build(nowNanos, viewport, run, speedPercent, QualityTier.FULL);
	}

	/**
	 * The same picture at a quality tier: only the decoration (glow, trails, shadows) differs, every other item is identical at every tier. The events
	 * that are fully detailed are chosen from the clock and the identities alone (FR-062); nothing here removes, merges, moves or
	 * reorders an event of the timeline, the judging or the MIDI playback: a hidden note is the same event, not painted.
	 */
	public Scene build(long nowNanos, Viewport viewport, ArcadeRun.View run, int speedPercent, QualityTier tier) {
		Out out = new Out(this.monochrome, this.theme);
		Frame f = new Frame(out, new IsometricProjection(viewport), this.camera.at(nowNanos), viewport, tier, nowNanos, this.camera.settings(), run);
		// the grid of the neck as it is painted, and the lanes built from it, once for this camera snapshot: the neck, the lanes and every marker below use these
		f.grid = PaintedFretGrid.of(this.neck, f.projection, f.cam);
		f.lanes = FretArrivalLaneMap.fromPaintedFrets(f.grid, f.projection);
		drawNeck(out, f, viewport);

		List<Integer> visible = visibleEvents(nowNanos, f.settings);
		int active = activeEvent(visible, run);
		Plan plan = plan(f, visible, active, speedPercent);
		f.noLabel = plan.noLabel;
		// the events of the admitted set are drawn, the farthest first; every other event is absent: no marker, no lane, no bar, no label (FR-062)
		for (int k = visible.size() - 1; k >= 0; k--) {
			int index = visible.get(k);
			if (plan.detailed.contains(index)) {
				drawDetailed(f, index, index == active, plan);
			} else if (plan.bars.contains(index)) {
				drawChordBar(f, index, plan);
			}
		}
		drawTargets(f, active);
		drawBadges(f, plan);
		drawHud(out, viewport, run, speedPercent, active >= 0 ? nextText(this.events.get(active)) : null);
		return new Scene(out.items, nowNanos, viewport, this.theme);
	}

	private static boolean isPlayable(ArcadeRenderEvent event) {
		return event.kind() == ArcadeRenderEvent.Kind.NOTE || event.kind() == ArcadeRenderEvent.Kind.CHORD;
	}

	/**
	 * The one event whose target is on the physical neck (FR-060): the earliest actionable event (a note or a chord) that is not completed. An event is
	 * completed when the run has a verdict for it; one that is still waiting, even past its time, stays the active one until it is judged or leaves the
	 * screen. The camera looks at later events, but only this one gets a ring or a ghost; when it completes the next takes its place in the same scene.
	 *
	 * @return its index, or -1 when no visible event is actionable
	 */
	int activeEvent(List<Integer> visible, ArcadeRun.View run) {
		for (int index : visible) {
			if (!isPlayable(this.events.get(index))) {
				continue;
			}
			boolean completed = run != null && index < run.states().size() && run.states().get(index).verdict() != ArcadeVerdict.WAITING;
			if (!completed) {
				return index;
			}
		}
		return -1;
	}

	/** The indices of the events that are on screen, in play order. */
	List<Integer> visibleEvents(long nowNanos, CameraTrack.Settings settings) {
		List<Integer> visible = new ArrayList<Integer>();
		int from = firstIndexAtOrAfter(nowNanos - settings.holdAfterNanos);
		for (int i = from; i < this.events.size(); i++) {
			long onset = this.events.get(i).onsetNanos();
			if (onset - nowNanos > settings.lookAheadNanos) {
				break;
			}
			RenderState state = RenderState.of(onset, nowNanos, settings.lookAheadNanos, settings.holdAfterNanos);
			if (state != RenderState.HIDDEN && state != RenderState.EXPIRED) {
				visible.add(i);
			}
		}
		return visible;
	}

	private int firstIndexAtOrAfter(long nanos) {
		int low = 0;
		int high = this.events.size();
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (this.events.get(middle).onsetNanos() < nanos) {
				low = middle + 1;
			} else {
				high = middle;
			}
		}
		return low;
	}

	/**
	 * The target on the physical neck (FR-060, FR-065): for the active event and for no other, one filled, fully opaque disk on each written string and fret,
	 * in the colour of its string, with a high-contrast outline of {@value #OUTLINE_PX} logical pixels. A single note has one disk, a chord all of its
	 * positions and only those, as one group. The core is the same size, at the same place and as opaque in every quality tier, in the fallback, at every
	 * window size and in every theme; a halo may surround it as decoration, it never replaces or weakens it.
	 */
	private void drawTargets(Frame f, int active) {
		if (active < 0) {
			return;
		}
		ArcadeRenderEvent event = this.events.get(active);
		int count = this.neck.layout().count();
		double spacing = count > 1 ? Math.abs(f.projection.stringY(1, count) - f.projection.stringY(0, count)) : 40.0;
		double diameter = Math.max(TARGET_MIN_PX * this.uiScale, Math.min(TARGET_MAX_PX * this.uiScale, TARGET_SPACING_SHARE * spacing));
		double radius = diameter / 2.0;
		for (ArcadeRenderEvent.Position position : event.positions()) {
			ScreenPoint at = f.projection.neck(this.neck.fretCentreX(position.fret()), this.neck.layout().rowOf(position.tgString()), count, f.cam);
			int colour = this.colours.of(position.tgString());
			if (f.tier.glow()) {
				f.out.disc(Role.TARGET_HALO, at.x(), at.y(), radius * 1.7, radius * 1.7, colour, 0.28, event.index(), position.tgString());
			}
			f.out.disc(Role.TARGET_DISK, at.x(), at.y(), radius, radius, colour, 1.0, event.index(), position.tgString());
			f.out.ring(Role.TARGET_OUTLINE, at.x(), at.y(), radius + OUTLINE_PX / 2.0, radius + OUTLINE_PX / 2.0, OUTLINE_COLOUR, 1.0, OUTLINE_PX, event.index(),
					position.tgString());
		}
	}

	/**
	 * The approach field: the predefined lane of every fret cell that is on the screen, from the horizon to the middle of the top edge of the cell, and a few
	 * lines across at fixed shares of the way. A note follows one of these lanes and no other line goes from the horizon to the neck.
	 */
	private void drawField(Out out, Frame f) {
		for (int fret = 0; fret < f.lanes.size(); fret++) {
			FretArrivalLaneMap.Lane lane = f.lanes.laneForFret(fret);
			if (!f.grid.isPainted(Math.max(0, fret - 1)) || !f.grid.isPainted(fret)) {
				continue;
			}
			out.line(Role.FIELD_GUIDE, lane.origin().x(), lane.origin().y(), lane.end().x(), lane.end().y(), WIRE_COLOUR, 0.16, 1.0, -1, 0);
		}
		for (double progress : new double[] { 0.0, 0.35, 0.65, 0.85 }) {
			ScreenPoint left = f.projection.approach(f.cam.left(), progress, f.cam);
			ScreenPoint right = f.projection.approach(f.cam.right(), progress, f.cam);
			out.line(Role.FIELD_GUIDE, left.x(), left.y(), right.x(), right.y(), WIRE_COLOUR, 0.12, 1.0, -1, 0);
		}
	}

	private void drawNeck(Out out, Frame f, Viewport viewport) {
		IsometricProjection projection = f.projection;
		Camera cam = f.cam;
		int count = this.neck.layout().count();
		drawField(out, f);
		double w = viewport.width();
		double h = viewport.height();
		// the top edge of the neck is where the lanes end
		double top = f.grid.neckTopY();
		double bottom = h * IsometricProjection.NECK_NEAR_EDGE;
		out.quad(Role.NECK_BODY, new double[] { 0, top, w, top, w, bottom, 0, bottom }, NECK_COLOUR, 1.0, -1, 0);
		double wireTop = projection.stringY(count - 1, count) - h * 0.012;
		double wireBottom = projection.stringY(0, count) + h * 0.012;
		double lastNumberRight = -1000.0;
		for (int n = 0; n <= this.neck.fretCount(); n++) {
			if (!f.grid.isPainted(n)) {
				continue;
			}
			double px = f.grid.wireX(n);
			out.line(n == 0 ? Role.NUT : Role.FRET_WIRE, px, wireTop, px, wireBottom, WIRE_COLOUR, 1.0, n == 0 ? 4.0 : 1.5, -1, 0);
			if (n >= 1) {
				// a number is drawn only whole: the wire of a fret may be on the screen while its middle, where the number goes, is not
				double centre = projection.neckX(this.neck.fretCentreX(n), cam);
				double half = Integer.toString(n).length() * h * 0.022 * 0.33;
				// and only where it does not touch the one before: in a wide view of high frets the numbers are close, and some are left out
				if (centre - half >= 0 && centre + half <= w && centre - half >= lastNumberRight + GAP) {
					out.text(Role.FRET_NUMBER, Integer.toString(n), centre, h * 0.905, h * 0.022, WIRE_COLOUR, 1.0, -1, 0);
					lastNumberRight = centre + half;
				}
			}
		}
		double middle = (wireTop + wireBottom) / 2.0;
		for (NeckModel.Marker marker : this.neck.markers()) {
			double x = this.neck.fretCentreX(marker.fret());
			if (!cam.shows(x, 0.0)) {
				continue;
			}
			double px = projection.neckX(x, cam);
			double r = h * 0.009;
			if (marker.isDouble()) {
				out.disc(Role.INLAY, px, middle - (middle - wireTop) * 0.45, r, r, INLAY_COLOUR, 1.0, -1, 0);
				out.disc(Role.INLAY, px, middle + (wireBottom - middle) * 0.45, r, r, INLAY_COLOUR, 1.0, -1, 0);
			} else {
				out.disc(Role.INLAY, px, middle, r, r, INLAY_COLOUR, 1.0, -1, 0);
			}
		}
		StringLayout layout = this.neck.layout();
		for (int row = 0; row < count; row++) {
			int tgString = layout.tgStringAtRow(row);
			double y = projection.stringY(row, count);
			int colour = this.colours.of(tgString);
			out.line(Role.STRING, 0, y, w, y, colour, 0.9, 1.0 + this.neck.gauge(row) * 1.2, -1, tgString);
			out.text(Role.STRING_LABEL, PitchNames.of(this.neck.openMidi(row)), w * 0.028, y, h * 0.026, colour, 1.0, -1, tgString, SceneItem.Align.LEFT);
		}
		out.line(Role.HIT_LINE, 0, projection.hitLineY(), w, projection.hitLineY(), HIT_COLOUR, 1.0, 2.0, -1, 0);
	}

	// ------------------------------------------------------------------------------------------------------- the density budget (FR-062)

	/** What one build needs everywhere: the output, the projection and the instant. */
	private static final class Frame {
		final Out out;
		final IsometricProjection projection;
		final Camera cam;
		final Viewport viewport;
		final QualityTier tier;
		final long now;
		final CameraTrack.Settings settings;
		final ArcadeRun.View run;
		final double h;
		PaintedFretGrid grid;
		java.util.Set<Integer> noLabel;
		FretArrivalLaneMap lanes;

		Frame(Out out, IsometricProjection projection, Camera cam, Viewport viewport, QualityTier tier, long now, CameraTrack.Settings settings, ArcadeRun.View run) {
			this.out = out;
			this.projection = projection;
			this.cam = cam;
			this.viewport = viewport;
			this.tier = tier;
			this.now = now;
			this.settings = settings;
			this.run = run;
			this.h = viewport.height();
		}

		RenderState stateOf(ArcadeRenderEvent event) {
			return RenderState.of(event.onsetNanos(), this.now, this.settings.lookAheadNanos, this.settings.holdAfterNanos);
		}

		double progressOf(ArcadeRenderEvent event) {
			return RenderState.progress(event.onsetNanos(), this.now, this.settings.lookAheadNanos);
		}
	}

	/** The result of the choice: which events are fully detailed, where the diagrams of the chords go, where the verdict badges go. */
	private static final class Plan {
		final java.util.Set<Integer> detailed = new java.util.HashSet<Integer>();
		final java.util.Map<Integer, double[]> badge = new java.util.HashMap<Integer, double[]>();
		/** The chords whose diagram is expanded: one, except in the unreduced picture. */
		final java.util.Set<Integer> expanded = new java.util.HashSet<Integer>();
		/** The notes whose fret number would touch another label: the marker is drawn, the number is left out. */
		final java.util.Set<Integer> noLabel = new java.util.HashSet<Integer>();
		/** The chords of the admitted visual set that have no diagram: they are timing bars. */
		final java.util.Set<Integer> bars = new java.util.HashSet<Integer>();
		/** How far a diagram was raised above its default place to leave the next event in view. */
		final java.util.Map<Integer, Double> raise = new java.util.HashMap<Integer, Double>();
		/** The names of the chords that are bars and that fit without touching anything. */
		final java.util.Map<Integer, double[]> chip = new java.util.HashMap<Integer, double[]>();
	}

	/** Where a note is on the screen, and how big. */
	private static final class NoteGeo {
		ScreenPoint at;
		double radius;
		double x;

		double[] box() {
			return new double[] { this.at.x() - this.radius, this.at.y() - this.radius, this.at.x() + this.radius, this.at.y() + this.radius };
		}
	}

	/** Where a chord bar is and where its diagram goes before any move. */
	private static final class ChordGeo {
		ScreenPoint pl;
		ScreenPoint pr;
		double thickness;
		double centreX;
		double barTop;
		double boxW;
		double boxH;
		double boxX;
		double boxY;

		double[] barBox() {
			return new double[] { Math.min(this.pl.x(), this.pr.x()), this.barTop, Math.max(this.pl.x(), this.pr.x()), this.pl.y() };
		}

		double[] cartoucheBox(double x, boolean note) {
			return new double[] { x, this.boxY - (note ? 14.0 : 0.0), x + this.boxW, this.boxY + this.boxH };
		}
	}

	private NoteGeo noteGeo(Frame f, ArcadeRenderEvent event) {
		NoteGeo g = new NoteGeo();
		ArcadeRenderEvent.Position position = event.positions().get(0);
		g.x = this.neck.fretCentreX(position.fret());
		// the lane of the fret of the note, whatever its string: the same line for every note written on that fret
		g.at = f.lanes.laneForFret(position.fret()).at(f.progressOf(event));
		// smaller in the distance than on the neck (the owner, 2026-10-09: the disks off the neck were too big), and adjustable by the learner
		g.radius = Math.max(6.0, f.h * 0.036 * this.markerSize * (0.35 + 0.65 * g.at.scale()));
		return g;
	}

	private ChordGeo chordGeo(Frame f, ArcadeRenderEvent event) {
		ChordGeo g = new ChordGeo();
		double progress = f.progressOf(event);
		// every chord bar is on the same axis, whatever the frets of the chord (FR-063): the point of the lane at this depth gives the height and the scale
		ScreenPoint lane = f.projection.approach(f.cam.centre(), progress, f.cam);
		double axis = f.viewport.width() * CHORD_AXIS;
		double half = f.viewport.width() * CHORD_HALF_WIDTH * lane.scale();
		g.pl = new ScreenPoint(axis - half, lane.y(), lane.scale());
		g.pr = new ScreenPoint(axis + half, lane.y(), lane.scale());
		g.thickness = Math.max(3.0, f.h * 0.014 * lane.scale());
		g.centreX = axis;
		g.barTop = lane.y() - g.thickness;
		if (event.kind() == ArcadeRenderEvent.Kind.CHORD) {
			ChordCartouche cartouche = this.cartouches[event.index()];
			double scale = lane.scale();
			double unit = f.h / 720.0;
			g.boxW = Math.max(ChordCartouche.MIN_WIDTH_PX, (20 + 18 * (cartouche.strings() - 1)) * unit * scale);
			g.boxH = Math.max(ChordCartouche.MIN_HEIGHT_PX, (24 + 14 * cartouche.rows()) * unit * 1.6 * scale);
			// the header carries the whole name as written: the diagram is made wide enough for it, never the name cut or shrunk below a readable size
			g.boxW = Math.max(g.boxW, headerWidth(cartouche, event, g.boxH));
			double stem = Math.max(6.0, f.h * 0.03 * scale);
			// centred on its bar, and kept whole inside the window
			g.boxX = Math.max(2.0, Math.min(f.viewport.width() - g.boxW - 2.0, g.centreX - g.boxW / 2.0));
			g.boxY = Math.max(cartouche.notCheckedSeparately() ? 16.0 : 2.0, g.barTop - stem - g.boxH);
		}
		return g;
	}

	/** The room of a text, estimated from its length and size: the widest letters of a bold sans-serif font. */
	private static double[] textBox(String text, double cx, double cy, double size) {
		double width = text.length() * size * 0.66;
		return new double[] { cx - width / 2.0, cy - size * 0.6, cx + width / 2.0, cy + size * 0.6 };
	}

	private static boolean disjoint(double[] a, double[] b) {
		return a[2] + GAP <= b[0] || b[2] + GAP <= a[0] || a[3] + GAP <= b[1] || b[3] + GAP <= a[1];
	}

	private static boolean disjointFromAll(double[] box, List<double[]> others) {
		for (double[] other : others) {
			if (!disjoint(box, other)) {
				return false;
			}
		}
		return true;
	}

	private static boolean inside(double[] box, Viewport viewport) {
		return box[0] >= 0 && box[1] >= 0 && box[2] <= viewport.width() && box[3] <= viewport.height();
	}

	private double[] badgeSize(Frame f, ArcadeRun.EventState result) {
		double badgeH = f.h * 0.036;
		return new double[] { f.viewport.width() * (missingNames(result).isEmpty() ? 0.15 : 0.22), badgeH };
	}

	/** The rows of the verdict badges, under the neck and its fret numbers. */
	private double[] badgeSlot(Frame f, double wantedX, double w, double hgt, List<double[]> placed) {
		double screen = f.viewport.width();
		double[] rows = { f.h * 0.925, f.h * 0.925 + hgt + 3.0 };
		List<Double> xs = new ArrayList<Double>();
		double base = Math.max(2.0, Math.min(screen - w - 2.0, wantedX - w / 2.0));
		xs.add(base);
		for (int k = 1; k <= 8; k++) {
			xs.add(base + k * (w + GAP));
			xs.add(base - k * (w + GAP));
		}
		for (double y : rows) {
			for (double x : xs) {
				double[] box = { x, y, x + w, y + hgt };
				if (x >= 2.0 && x + w <= screen - 2.0 && y + hgt <= f.viewport.height() - 1.0 && disjointFromAll(box, placed)) {
					return box;
				}
			}
		}
		return null;
	}

	/** The boxes of the HUD, which no label may cover. */
	private List<double[]> hudBoxes(Frame f, ArcadeRun.View run, int speedPercent, String next) {
		List<double[]> boxes = new ArrayList<double[]>();
		double size = f.h * 0.034;
		double w = f.viewport.width();
		if (run != null) {
			String text = this.labels.streak(run.streak());
			boxes.add(new double[] { w * 0.03, f.h * 0.05 - size * 0.6, w * 0.03 + text.length() * size * 0.66, f.h * 0.05 + size * 0.6 });
		}
		String speed = this.labels.speed(speedPercent);
		boxes.add(new double[] { w * 0.97 - speed.length() * size * 0.66, f.h * 0.05 - size * 0.6, w * 0.97, f.h * 0.05 + size * 0.6 });
		if (next != null) {
			double small = f.h * 0.026;
			boxes.add(new double[] { w * 0.97 - next.length() * small * 0.66, f.h * 0.098 - small * 0.6, w * 0.97, f.h * 0.098 + small * 0.6 });
		}
		return boxes;
	}

	/**
	 * Chooses what the picture holds (FR-062): every event that is not completed and due within the look-ahead, in onset order, notes, chords and automatically
	 * passed events alike, whatever their number; nothing else is drawn. The markers of the notes are always drawn; the fret number of a note is left out only
	 * where it would touch a label already placed (the nearest notes are placed first, the active event always), and so are the words of automatic events. Of
	 * the chords of the set the nearest that can be read has the diagram; the others are timing bars (FR-063). The events that are completed are not in the
	 * set: only the verdict badge of each stays during its feedback. It is a function of the clock, the run and the identities alone.
	 */
	private Plan plan(Frame f, List<Integer> visible, int active, int speedPercent) {
		Plan plan = new Plan();
		List<double[]> taken = new ArrayList<double[]>(hudBoxes(f, f.run, speedPercent, active >= 0 ? nextText(this.events.get(active)) : null));
		List<double[]> badges = new ArrayList<double[]>();
		// the verdict of an event that is completed stays as a badge for the length of its feedback; its marker, lane and target are gone
		for (int index : visible) {
			ArcadeRenderEvent event = this.events.get(index);
			RenderState state = f.stateOf(event);
			if (isCompleted(f, index) && state.ordinal() >= RenderState.ARMED.ordinal()) {
				double[] size = badgeSize(f, f.run.states().get(index));
				double[] slot = badgeSlot(f, f.projection.neckX(columnOf(event), f.cam), size[0], size[1], badges);
				if (slot != null) {
					plan.badge.put(index, slot);
					badges.add(slot);
				}
			}
		}
		if (active < 0) {
			return plan;
		}
		// every event that is not completed and due within the look-ahead (an event of the score that is passed automatically and comes before the active
		// one is one of them), in onset order: the visible set, whatever the number of events
		List<Integer> candidates = new ArrayList<Integer>();
		for (int index : visible) {
			if (!isCompleted(f, index) && (index >= active || !isPlayable(this.events.get(index)))) {
				candidates.add(index);
			}
		}
		int expandedChord = expandedChord(f, candidates, active);
		for (int index : candidates) {
			ArcadeRenderEvent event = this.events.get(index);
			boolean isActive = index == active;
			RenderState state = f.stateOf(event);
			double[] next = nextPlayableFootprint(f, visible, index);
			List<double[]> mine = new ArrayList<double[]>();
			boolean bar = false;
			if (event.kind() == ArcadeRenderEvent.Kind.NOTE) {
				// the marker is always drawn; its fret number only where it touches no label already placed (the nearest notes are placed first)
				double[] box = noteGeo(f, event).box();
				boolean free = disjointFromAll(box, taken) && inside(box, f.viewport) && (next == null || disjoint(box, next));
				if (this.densityBudget && !isActive && !free) {
					plan.noLabel.add(index);
				} else {
					mine.add(box);
				}
			} else if (event.kind() == ArcadeRenderEvent.Kind.CHORD && index == expandedChord) {
				ChordGeo g = chordGeo(f, event);
				boolean note = this.cartouches[index].notCheckedSeparately() && state.ordinal() >= RenderState.READABLE.ordinal();
				List<double[]> obstacles = new ArrayList<double[]>(taken);
				if (next != null) {
					obstacles.add(next);
				}
				// the diagram stays centred on its bar and inside the window; to leave the next playable event in view it can only be raised, by a longer stem
				double raise = 0.0;
				boolean placed = false;
				for (double lift = 0.0; g.boxY - lift >= 2.0 + (note ? 14.0 : 0.0) && !placed; lift += 12.0) {
					double[] box = g.cartoucheBox(g.boxX, note);
					box[1] -= lift;
					box[3] -= lift;
					if (disjointFromAll(box, obstacles)) {
						raise = lift;
						placed = true;
					}
				}
				double[] box = g.cartoucheBox(g.boxX, note);
				box[1] -= raise;
				box[3] -= raise;
				mine.add(box);
				plan.raise.put(index, raise);
				plan.expanded.add(index);
			} else if (event.kind() == ArcadeRenderEvent.Kind.CHORD) {
				bar = true;
			} else {
				ChordGeo g = chordGeo(f, event);
				String word = event.kind() == ArcadeRenderEvent.Kind.AUTOMATIC ? this.labels.automatic() : this.labels.unsupported();
				mine.add(textBox(word, g.centreX, g.barTop - f.h * 0.025, f.h * 0.026));
			}
			if (this.densityBudget && !isActive && !bar && event.kind() != ArcadeRenderEvent.Kind.NOTE) {
				boolean free = true;
				for (double[] box : mine) {
					free &= disjointFromAll(box, taken) && inside(box, f.viewport) && (next == null || disjoint(box, next));
				}
				if (!free) {
					// the word of an automatic event that would touch another label is left out with the event: it is passed by itself
					continue;
				}
			}
			if (bar) {
				plan.bars.add(index);
			} else {
				plan.detailed.add(index);
			}
			taken.addAll(mine);
		}
		// the names of the chords that are bars, nearest first, where nothing is touched
		List<Integer> chords = new ArrayList<Integer>(plan.bars);
		java.util.Collections.sort(chords);
		for (int index : chords) {
			ArcadeRenderEvent event = this.events.get(index);
			if (event.display().source() == ChordNameResolver.Source.PITCH_SET) {
				continue;
			}
			ChordGeo g = chordGeo(f, event);
			double size = Math.max(9.0, f.h * 0.022);
			double[] box = textBox(event.display().text(), g.centreX, g.barTop - size, size);
			if (inside(box, f.viewport) && disjointFromAll(box, taken)) {
				plan.chip.put(index, box);
				taken.add(box);
			}
		}
		return plan;
	}

	/**
	 * The chord of the admitted set whose diagram is expanded (FR-063): the active event when it is a chord; otherwise the nearest admitted chord that has
	 * reached the readable state. One at most, so that a frame never holds two diagrams; when it completes the next chord of the set takes its place in the same
	 * update. Nothing here moves a bar: the onsets and the depths are those of the timeline.
	 *
	 * @param set the events that may be admitted, the active one first, in onset order
	 * @return its index, or -1
	 */
	int expandedChord(Frame f, List<Integer> set, int active) {
		if (active >= 0 && this.events.get(active).kind() == ArcadeRenderEvent.Kind.CHORD) {
			return active;
		}
		for (int index : set) {
			ArcadeRenderEvent event = this.events.get(index);
			if (event.kind() != ArcadeRenderEvent.Kind.CHORD) {
				continue;
			}
			RenderState state = f.stateOf(event);
			if (state == RenderState.READABLE || state == RenderState.ARMED || state == RenderState.IMPACT) {
				return index;
			}
		}
		return -1;
	}

	/** True for an event that has its verdict: it is completed, whatever the state of its time. */
	private static boolean isCompleted(Frame f, int index) {
		return f.run != null && index < f.run.states().size() && f.run.states().get(index).verdict() != ArcadeVerdict.WAITING;
	}

	/** True for an event that has its verdict and is past its impact: its number is no longer needed. */
	private static boolean isJudged(Frame f, int index, RenderState state) {
		return state.ordinal() >= RenderState.IMPACT.ordinal() && f.run != null && index < f.run.states().size() && f.run.states().get(index).verdict() != ArcadeVerdict.WAITING;
	}

	/** The room taken by the next playable event after this one when it is a note: its marker; null when it is a chord, see below. */
	private double[] nextPlayableFootprint(Frame f, List<Integer> visible, int index) {
		boolean after = false;
		for (int other : visible) {
			if (other == index) {
				after = true;
				continue;
			}
			if (after && isPlayable(this.events.get(other))) {
				ArcadeRenderEvent event = this.events.get(other);
				// a next note must stay in view; the bar of a next chord is on the same axis as this diagram by rule (FR-063): it passes behind it and stays
				// visible on both sides, because a bar is much wider than a diagram
				return event.kind() == ArcadeRenderEvent.Kind.NOTE ? noteGeo(f, event).box() : null;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------------------------------------------- drawing

	private double alphaOf(RenderState state, long nowNanos, long onset, CameraTrack.Settings settings) {
		switch (state) {
			case DISTANT:
				return 0.55;
			case READABLE:
				return 0.8;
			case FEEDBACK:
				double span = Math.max(1.0, settings.holdAfterNanos - RenderState.IMPACT_NANOS);
				return 1.0 - 0.8 * Math.min(1.0, (nowNanos - onset - RenderState.IMPACT_NANOS) / span);
			default:
				return 1.0;
		}
	}

	/**
	 * An event that is not in the detailed set (FR-062): a small marker in the colour of its string, or a thin neutral bar, on its own lane and at its own
	 * distance. No fret number, no diagram, no glow, no trail, no trajectory, nothing on the neck.
	 */
	private void drawChordBar(Frame f, int index, Plan plan) {
		ArcadeRenderEvent event = this.events.get(index);
		RenderState state = f.stateOf(event);
		double alpha = alphaOf(state, f.now, event.onsetNanos(), f.settings) * 0.8;
		{
			ChordGeo g = chordGeo(f, event);
			double t = Math.max(2.0, g.thickness * 0.5);
			f.out.quad(Role.CHORD_BAR, new double[] { g.pl.x(), g.pl.y(), g.pr.x(), g.pr.y(), g.pr.x(), g.pr.y() - t, g.pl.x(), g.pl.y() - t }, BAR_COLOUR, alpha,
					event.index(), 0);
			double[] chip = plan.chip.get(index);
			if (chip != null) {
				f.out.text(Role.CHORD_CHIP, event.display().text(), (chip[0] + chip[2]) / 2.0, (chip[1] + chip[3]) / 2.0, Math.max(9.0, f.h * 0.022), TEXT_COLOUR, alpha,
						event.index(), 0);
			}
		}
	}

	private void drawDetailed(Frame f, int index, boolean active, Plan plan) {
		ArcadeRenderEvent event = this.events.get(index);
		RenderState state = f.stateOf(event);
		double alpha = alphaOf(state, f.now, event.onsetNanos(), f.settings);
		if (event.kind() == ArcadeRenderEvent.Kind.NOTE) {
			drawNote(f, event, state, alpha, active);
		} else if (event.kind() == ArcadeRenderEvent.Kind.CHORD && !plan.expanded.contains(index)) {
			drawChordBar(f, index, plan);
		} else {
			drawBarEvent(f, event, state, alpha, active, plan.raise.get(index));
		}
	}

	private void drawNote(Frame f, ArcadeRenderEvent event, RenderState state, double alpha, boolean active) {
		Out out = f.out;
		ArcadeRenderEvent.Position position = event.positions().get(0);
		int count = this.neck.layout().count();
		int row = this.neck.layout().rowOf(position.tgString());
		int colour = this.colours.of(position.tgString());
		NoteGeo g = noteGeo(f, event);
		FretArrivalLaneMap.Lane lane = f.lanes.laneForFret(position.fret());
		ScreenPoint far = lane.origin();
		// a note that has its verdict is gone from the lane: the badge says the rest, and the next note of the same fret, which comes on the same lane, finds it free
		if (isJudged(f, event.index(), state)) {
			return;
		}
		// the trajectory of a note is its lane, the one of its fret (FR-066): from the horizon to the middle of the top edge of its cell, where it stops; nothing
		// continues across the neck to the string, the target disk of the neck tells the string
		ScreenPoint end = lane.end();
		out.line(Role.TRAJECTORY, far.x(), far.y(), end.x(), end.y(), colour, alpha * 0.6, 2.0, event.index(), position.tgString());
		if (f.tier.trails()) {
			ScreenPoint behind = lane.at(Math.max(0.0, f.progressOf(event) - 0.12));
			out.line(Role.TRAIL, behind.x(), behind.y(), g.at.x(), g.at.y(), colour, alpha * 0.35, g.radius, event.index(), position.tgString());
		}
		if (f.tier.glow()) {
			out.disc(Role.GLOW, g.at.x(), g.at.y(), g.radius * 1.9, g.radius * 1.9, colour, alpha * 0.22, event.index(), position.tgString());
		}
		out.disc(Role.MARKER, g.at.x(), g.at.y(), g.radius, g.radius, colour, alpha, event.index(), position.tgString());
		if (f.noLabel == null || !f.noLabel.contains(event.index())) {
			out.text(Role.MARKER_FRET, Integer.toString(position.fret()), g.at.x(), g.at.y(), Math.max(8.0, g.radius * 1.1), 0xFFFFFF, alpha, event.index(), position.tgString());
		}
	}

	private void drawBarEvent(Frame f, ArcadeRenderEvent event, RenderState state, double alpha, boolean active, Double raise) {
		Out out = f.out;
		ChordGeo g = chordGeo(f, event);
		out.quad(Role.TIMING_BAR, new double[] { g.pl.x(), g.pl.y(), g.pr.x(), g.pr.y(), g.pr.x(), g.pr.y() - g.thickness, g.pl.x(), g.pl.y() - g.thickness },
				BAR_COLOUR, alpha, event.index(), 0);
		if (event.kind() != ArcadeRenderEvent.Kind.CHORD) {
			String word = event.kind() == ArcadeRenderEvent.Kind.AUTOMATIC ? this.labels.automatic() : this.labels.unsupported();
			out.text(Role.EVENT_TEXT, word, g.centreX, g.barTop - f.h * 0.025, f.h * 0.026, TEXT_COLOUR, alpha, event.index(), 0);
			return;
		}
		ChordCartouche cartouche = this.cartouches[event.index()];
		double boxX = g.boxX;
		double boxY = g.boxY - (raise != null ? raise : 0.0);
		out.line(Role.STEM, g.centreX, g.barTop, g.centreX, boxY + g.boxH, BAR_COLOUR, alpha, 2.0, event.index(), 0);
		if (f.tier.shadows()) {
			out.quad(Role.SHADOW, new double[] { boxX + 4, boxY + 5, boxX + g.boxW + 4, boxY + 5, boxX + g.boxW + 4, boxY + g.boxH + 5, boxX + 4, boxY + g.boxH + 5 },
					0x000000, alpha * 0.35, event.index(), 0);
		}
		drawCartouche(out, cartouche, event, state, boxX, boxY, g.boxW, g.boxH, alpha);
	}

	private static final double HEADER_FONT_MIN = 9.0;

	/** The size of the font of the header for a diagram of this height. */
	private static double headerSize(double boxHeight) {
		return Math.max(HEADER_FONT_MIN, boxHeight * 0.22 * 0.62);
	}

	/** The text of the header of a chord: its written or derived name, or the notes introduced by the word of the interface; never a bare generic word. */
	private String headerText(ArcadeRenderEvent event) {
		ChordNameResolver.Display display = event.display();
		return display.source() == ChordNameResolver.Source.PITCH_SET ? this.labels.pitchSet(display.text()) : display.text();
	}

	/** The width the header needs: the name (or the generic word), the arrow of the stroke, and a margin. */
	private double headerWidth(ChordCartouche cartouche, ArcadeRenderEvent event, double boxHeight) {
		String name = headerText(event);
		double size = headerSize(boxHeight);
		return name.length() * size * 0.66 + (cartouche.stroke() != ArcadeRenderEvent.Stroke.NONE ? size * 1.4 : 0.0) + 14.0;
	}

	private void drawCartouche(Out out, ChordCartouche cartouche, ArcadeRenderEvent event, RenderState state, double x, double y, double w, double h,
			double alpha) {
		int strings = cartouche.strings();
		out.quad(Role.CARTOUCHE_FRAME, new double[] { x, y, x + w, y, x + w, y + h, x, y + h }, FRAME_COLOUR, alpha, event.index(), 0);
		double nameBand = h * 0.22;
		double markBand = h * 0.12;
		out.quad(Role.CARTOUCHE_HEADER, new double[] { x, y, x + w, y, x + w, y + nameBand, x, y + nameBand }, HEADER_COLOUR, Math.max(alpha, 0.9), event.index(), 0);
		double gridTop = y + nameBand + markBand;
		double gridBottom = y + h - h * 0.04;
		double gridLeft = x + w * 0.34;
		double gridRight = x + w - w * 0.10;
		double columnGap = strings == 1 ? 0.0 : (gridRight - gridLeft) / (strings - 1);
		double rowHeight = (gridBottom - gridTop) / cartouche.rows();
		String name = headerText(event);
		double nameSize = headerSize(h);
		boolean arrow = cartouche.stroke() != ArcadeRenderEvent.Stroke.NONE;
		double arrowWidth = arrow ? nameSize * 1.4 : 0.0;
		// the header stays at its full strength from the first state to the end of the feedback: the name is what the learner reads to know the chord
		double headerAlpha = Math.max(alpha, 0.9);
		out.text(Role.CARTOUCHE_NAME, name, x + (w - arrowWidth) / 2.0, y + nameBand / 2.0, nameSize, HEADER_TEXT_COLOUR, headerAlpha, event.index(), 0);
		if (arrow) {
			out.text(Role.CARTOUCHE_STROKE, cartouche.stroke() == ArcadeRenderEvent.Stroke.DOWN ? "\u2193" : "\u2191", x + w - arrowWidth / 2.0 - 3.0, y + nameBand / 2.0,
					nameSize, HEADER_TEXT_COLOUR, headerAlpha, event.index(), 0);
		}
		for (int column = 0; column < strings; column++) {
			double cx = gridLeft + column * columnGap;
			out.line(Role.CARTOUCHE_STRING, cx, gridTop, cx, gridBottom, DIAGRAM_LINE, alpha, 1.0, event.index(), 0);
			ChordCartouche.Mark mark = cartouche.markAt(column);
			if (mark != ChordCartouche.Mark.NONE) {
				out.text(Role.CARTOUCHE_MARK, mark == ChordCartouche.Mark.OPEN ? "O" : "X", cx, y + nameBand + markBand / 2.0, Math.max(7.0, markBand * 0.8),
						TEXT_COLOUR, alpha, event.index(), this.neck.layout().tgStringAtRow(column));
			}
		}
		for (int line = 0; line <= cartouche.rows(); line++) {
			double ly = gridTop + line * rowHeight;
			out.line(Role.CARTOUCHE_FRET, gridLeft, ly, gridRight, ly, DIAGRAM_LINE, alpha, line == 0 && cartouche.firstFret() == 1 ? 3.0 : 1.0, event.index(), 0);
		}
		if (cartouche.fretLabel() != null) {
			out.text(Role.CARTOUCHE_FRET_LABEL, cartouche.fretLabel(), x + w * 0.13, gridTop + rowHeight / 2.0, Math.max(7.0, rowHeight * 0.55), TEXT_COLOUR,
					alpha, event.index(), 0);
		}
		double dotRadius = Math.max(2.5, Math.min(columnGap == 0.0 ? rowHeight : columnGap, rowHeight) * 0.38);
		for (ChordCartouche.Dot dot : cartouche.dots()) {
			out.disc(Role.CARTOUCHE_DOT, gridLeft + dot.column() * columnGap, gridTop + (dot.row() + 0.5) * rowHeight, dotRadius, dotRadius,
					this.colours.of(dot.tgString()), alpha, event.index(), dot.tgString());
		}
		if (cartouche.notCheckedSeparately() && state.ordinal() >= RenderState.READABLE.ordinal()) {
			out.text(Role.CARTOUCHE_NOTE, this.labels.notCheckedSeparately(), x + w / 2.0, y - 8.0, 10.0, TEXT_COLOUR, alpha, event.index(), 0);
		}
	}

	private void drawBadges(Frame f, Plan plan) {
		if (f.run == null) {
			return;
		}
		List<Integer> indices = new ArrayList<Integer>(plan.badge.keySet());
		java.util.Collections.sort(indices);
		for (int index : indices) {
			ArcadeRenderEvent event = this.events.get(index);
			RenderState state = f.stateOf(event);
			ArcadeRun.EventState result = f.run.states().get(index);
			double[] box = plan.badge.get(index);
			double badgeW = box[2] - box[0];
			double badgeH = box[3] - box[1];
			double alpha = alphaOf(state, f.now, event.onsetNanos(), f.settings);
			String names = missingNames(result);
			int colour = verdictColour(result.verdict());
			f.out.quad(Role.BADGE, new double[] { box[0], box[1], box[2], box[1], box[2], box[3], box[0], box[3] }, colour, alpha, index, 0);
			f.out.text(Role.BADGE_SYMBOL, verdictSymbol(result.verdict()), box[0] + badgeH * 0.6, box[1] + badgeH / 2.0, badgeH * 0.7, 0x101216, alpha, index, 0);
			String word = this.labels.verdict(result.verdict());
			f.out.text(Role.BADGE_TEXT, names.isEmpty() ? word : word + " \u00B7 " + this.labels.missing(names), box[0] + badgeW / 2.0 + badgeH * 0.3,
					box[1] + badgeH / 2.0, badgeH * 0.55, 0x101216, alpha, index, 0);
		}
	}

	private static String missingNames(ArcadeRun.EventState result) {
		StringBuilder names = new StringBuilder();
		for (int midi : result.missingPitches()) {
			names.append(names.length() > 0 ? " " : "").append(PitchNames.of(midi));
		}
		return names.toString();
	}

	/** The middle of what an event targets along the neck, in millimetres. */
	private double columnOf(ArcadeRenderEvent event) {
		if (event.positions().isEmpty()) {
			return this.neck.fretX(this.neck.fretCount() / 2);
		}
		double sum = 0.0;
		for (ArcadeRenderEvent.Position position : event.positions()) {
			sum += this.neck.fretCentreX(position.fret());
		}
		return sum / event.positions().size();
	}

	private void drawHud(Out out, Viewport viewport, ArcadeRun.View run, int speedPercent, String next) {
		double h = viewport.height();
		if (run != null) {
			out.text(Role.HUD_STREAK, this.labels.streak(run.streak()), viewport.width() * 0.03, h * 0.05, h * 0.034, TEXT_COLOUR, 1.0, -1, 0, SceneItem.Align.LEFT);
		}
		out.text(Role.HUD_SPEED, this.labels.speed(speedPercent), viewport.width() * 0.97, h * 0.05, h * 0.034, TEXT_COLOUR, 1.0, -1, 0, SceneItem.Align.RIGHT);
		if (next != null) {
			// the next expected event, under the speed: what to play next, said in words (the score of the run is never shown while playing)
			out.text(Role.HUD_NEXT, next, viewport.width() * 0.97, h * 0.098, h * 0.026, TEXT_COLOUR, 0.85, -1, 0, SceneItem.Align.RIGHT);
		}
	}

	/** What the HUD says of the next event to play: a note by its pitch, string and fret, a chord by its name. */
	private String nextText(ArcadeRenderEvent event) {
		if (event.kind() == ArcadeRenderEvent.Kind.NOTE) {
			ArcadeRenderEvent.Position position = event.positions().get(0);
			return this.labels.nextNote(position.tgString(), position.fret());
		}
		return this.labels.nextChord(headerText(event));
	}

	/** Collects the items, removing the colour when the scene is monochrome. */
	private static final class Out {
		final List<SceneItem> items = new ArrayList<SceneItem>();
		private final boolean monochrome;

		private final Theme theme;

		Out(boolean monochrome, Theme theme) {
			this.monochrome = monochrome;
			this.theme = theme;
		}

		private int colour(int dark) {
			int rgb = this.theme.map(dark);
			if (!this.monochrome) {
				return rgb;
			}
			int grey = (int) Math.round(0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF));
			return (grey << 16) | (grey << 8) | grey;
		}

		void line(Role role, double x1, double y1, double x2, double y2, int colour, double alpha, double width, int event, int string) {
			this.items.add(new SceneItem(Shape.LINE, role, new double[] { x1, y1, x2, y2 }, colour(colour), alpha, width, null, event, string, null));
		}

		void quad(Role role, double[] corners, int colour, double alpha, int event, int string) {
			this.items.add(new SceneItem(Shape.QUAD, role, corners, colour(colour), alpha, 0.0, null, event, string, null));
		}

		void disc(Role role, double cx, double cy, double rx, double ry, int colour, double alpha, int event, int string) {
			this.items.add(new SceneItem(Shape.DISC, role, new double[] { cx, cy, rx, ry }, colour(colour), alpha, 0.0, null, event, string, null));
		}

		void ring(Role role, double cx, double cy, double rx, double ry, int colour, double alpha, double width, int event, int string) {
			this.items.add(new SceneItem(Shape.RING, role, new double[] { cx, cy, rx, ry }, colour(colour), alpha, width, null, event, string, null));
		}

		void text(Role role, String text, double x, double y, double size, int colour, double alpha, int event, int string) {
			text(role, text, x, y, size, colour, alpha, event, string, SceneItem.Align.CENTRE);
		}

		void text(Role role, String text, double x, double y, double size, int colour, double alpha, int event, int string, SceneItem.Align align) {
			this.items.add(new SceneItem(Shape.TEXT, role, new double[] { x, y, size }, colour(colour), alpha, 0.0, text, event, string, align));
		}
	}
}
