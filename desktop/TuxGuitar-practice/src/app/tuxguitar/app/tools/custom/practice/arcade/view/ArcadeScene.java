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
	static final int PANEL_BODY_COLOUR = 0xBFE9FF;
	static final int PANEL_EDGE_COLOUR = 0x9FE8F0;
	static final int PANEL_FRET_COLOUR = 0x10151C;
	/** The body of the neck is a faint tint: the scene behind stays visible (FR-075). */
	static final double NECK_ALPHA = 0.10;
	/** The number of the fret of the active event on the ruler of the neck. */
	static final int RULER_ACTIVE_COLOUR = 0xFFB347;
	static final int BAND_COLOUR = 0x2D5FA6;
	static final int BAND_LANE_COLOUR = 0x8FBBFF;
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
	/** The alpha of a string that no event of the active moment targets (FR-068); the string of the active event is at 1. */
	static final double STRING_DIM_ALPHA = 0.35;
	/** At the setting of 100 % a marker lands on the size of its target rectangle (owner, 2026-10-11, FR-073); at 0 % it is one pixel across. */
	static final double MARKER_MIN_RADIUS = 0.5;
	/** The gap kept between two labels, in pixels. */
	static final double GAP = 4.0;

	private boolean densityBudget = true;
	private Theme theme = Theme.DARK;
	private double uiScale = 1.0;
	private double markerSize = 1.0;
	private double farScale = IsometricProjection.DEFAULT_FAR_SCALE;
	private double horizon = IsometricProjection.DEFAULT_HORIZON;

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
		copy.markerSize = Math.max(0.0, Math.min(2.0, factor));
		return copy;
	}

	/**
	 * The same scene with another tilt of the plane (the learner's setting): {@code farScale} is the scale at the horizon, the lower the more tilted, and
	 * {@code horizon} the height of the horizon as a share of the height of the picture.
	 */
	public ArcadeScene withPerspective(double farScale, double horizon) {
		ArcadeScene copy = copy();
		copy.farScale = Math.max(0.05, Math.min(1.0, farScale));
		copy.horizon = Math.max(0.0, Math.min(0.5, horizon));
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
		copy.farScale = this.farScale;
		copy.horizon = this.horizon;
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
		Frame f = new Frame(out, new IsometricProjection(viewport, this.farScale, this.horizon), this.camera.at(nowNanos), viewport, tier, nowNanos, this.camera.settings(), run);
		// the grid of the neck as it is painted, and the lanes built from it, once for this camera snapshot: the neck, the lanes and every marker below use these
		f.grid = PaintedFretGrid.of(this.neck, f.projection, f.cam);
		f.lanes = FretArrivalLaneMap.fromPaintedFrets(f.grid, f.projection);
		List<Integer> visible = visibleEvents(nowNanos, f.settings);
		int active = activeEvent(visible, run);
		f.activeFrets = activeFrets(active);
		drawNeck(out, f, viewport, litStrings(active));
		Plan plan = plan(f, visible, active, speedPercent);
		f.noLabel = plan.noLabel;
		// the events of the admitted set are drawn, the farthest first; every other event is absent: no marker, no lane, no bar, no label (FR-062)
		for (int k = visible.size() - 1; k >= 0; k--) {
			int index = visible.get(k);
			if (plan.detailed.contains(index)) {
				drawDetailed(f, index, index == active, plan);
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
	/** Half the height of the target rectangle on the neck (FR-065, FR-070): the rule of the former diameter; the flying marker lands on this size. */
	private double targetHalfHeight(Frame f) {
		int count = this.neck.layout().count();
		double spacing = count > 1 ? Math.abs(f.projection.stringY(1, count) - f.projection.stringY(0, count)) : 40.0;
		return Math.max(TARGET_MIN_PX * this.uiScale, Math.min(TARGET_MAX_PX * this.uiScale, TARGET_SPACING_SHARE * spacing)) / 2.0;
	}

	/** Half the width of a rectangle on a fret: the cell between its two wires, less a margin (the target's rule), at the depth given. */
	private double halfWidthOfCell(Frame f, int fret, double progress) {
		double cell = Math.abs(f.projection.approach(this.neck.fretX(fret), progress, f.cam).x() - f.projection.approach(this.neck.fretX(fret - 1), progress, f.cam).x());
		return (cell - Math.max(3.0, cell * 0.14)) / 2.0;
	}

	private void drawTargets(Frame f, int active) {
		if (active < 0) {
			return;
		}
		ArcadeRenderEvent event = this.events.get(active);
		int count = this.neck.layout().count();
		double radius = targetHalfHeight(f);
		for (ArcadeRenderEvent.Position position : event.positions()) {
			if (position.fret() == 0) {
				// an open string has no target on the neck: its bar lands and the string is lit (FR-069)
				continue;
			}
			ScreenPoint at = f.projection.neck(this.neck.fretCentreX(position.fret()), this.neck.layout().rowOf(position.tgString()), count, f.cam);
			// the rectangle fills the cell of the fret between its two painted wires, less a margin (FR-070)
			double halfWidth = Math.max(radius * 0.7, halfWidthOfCell(f, position.fret(), 1.0));
			int colour = this.colours.of(position.tgString());
			if (f.tier.glow()) {
				f.out.rounded(Role.TARGET_HALO, at.x(), at.y(), halfWidth * 1.12, radius * 1.6, colour, 0.28, event.index(), position.tgString());
			}
			f.out.rounded(Role.TARGET_RECT, at.x(), at.y(), halfWidth, radius, colour, 1.0, event.index(), position.tgString());
			f.out.roundedRing(Role.TARGET_OUTLINE, at.x(), at.y(), halfWidth + OUTLINE_PX / 2.0, radius + OUTLINE_PX / 2.0, OUTLINE_COLOUR, 1.0, OUTLINE_PX, event.index(), position.tgString());
		}
	}

	/**
	 * The approach field: the predefined lane of every fret cell that is on the screen, from the horizon to the middle of the top edge of the cell, and a few
	 * lines across at fixed shares of the way. A note follows one of these lanes and no other line goes from the horizon to the neck.
	 */
	private void drawField(Out out, Frame f) {
		if (f.tier.glow()) {
			// the approach band (FR-072): a faint blue zone behind the lanes, from the horizon to the neck, the lane of the active fret a little lighter
			ScreenPoint farLeft = f.projection.approach(f.cam.left(), 0.0, f.cam);
			ScreenPoint farRight = f.projection.approach(f.cam.right(), 0.0, f.cam);
			ScreenPoint nearLeft = f.projection.approach(f.cam.left(), 1.0, f.cam);
			ScreenPoint nearRight = f.projection.approach(f.cam.right(), 1.0, f.cam);
			out.quad(Role.APPROACH_BAND, new double[] { farLeft.x(), farLeft.y(), farRight.x(), farRight.y(), nearRight.x(), nearRight.y(), nearLeft.x(), nearLeft.y() }, BAND_COLOUR, 0.16, -1, 0);
			// the band goes on under the neck, which is see-through: the strings float over it
			double neckBottom = f.h * IsometricProjection.NECK_NEAR_EDGE;
			out.quad(Role.APPROACH_BAND, new double[] { nearLeft.x(), nearLeft.y(), nearRight.x(), nearRight.y(), nearRight.x(), neckBottom, nearLeft.x(), neckBottom }, BAND_COLOUR, 0.16, -1, 0);
			for (int fret : f.activeFrets) {
				if (f.cam.shows(this.neck.fretX(fret - 1), 0.0) || f.cam.shows(this.neck.fretX(fret), 0.0)) {
					ScreenPoint a0 = f.projection.approach(this.neck.fretX(fret - 1), 0.0, f.cam);
					ScreenPoint b0 = f.projection.approach(this.neck.fretX(fret), 0.0, f.cam);
					ScreenPoint a1 = f.projection.approach(this.neck.fretX(fret - 1), 1.0, f.cam);
					ScreenPoint b1 = f.projection.approach(this.neck.fretX(fret), 1.0, f.cam);
					out.quad(Role.APPROACH_BAND, new double[] { a0.x(), a0.y(), b0.x(), b0.y(), b1.x(), b1.y(), a1.x(), a1.y() }, BAND_LANE_COLOUR, 0.14, -1, 0);
				}
			}
		}
		// the neck is see-through: the lanes go on under it, down to its bottom, as faint lines (FR-066 amended, FR-075)
		double neckBottomY = f.h * IsometricProjection.NECK_NEAR_EDGE;
		for (int fret = 1; fret < f.lanes.size(); fret++) {
			FretArrivalLaneMap.Lane lane = f.lanes.laneForFret(fret);
			if (!f.grid.isPainted(Math.max(0, fret - 1)) || !f.grid.isPainted(fret) || lane.end().y() <= lane.origin().y()) {
				continue;
			}
			double x2 = lane.end().x();
			out.line(Role.NECK_LANE, lane.end().x(), lane.end().y(), x2, neckBottomY, WIRE_COLOUR, 0.10, 1.0, -1, 0);
		}
		// no lane for fret 0: an open string is a bar across the lanes (FR-069)
		for (int fret = 1; fret < f.lanes.size(); fret++) {
			FretArrivalLaneMap.Lane lane = f.lanes.laneForFret(fret);
			if (!f.grid.isPainted(Math.max(0, fret - 1)) || !f.grid.isPainted(fret)) {
				continue;
			}
			out.line(Role.FIELD_GUIDE, lane.origin().x(), lane.origin().y(), lane.end().x(), lane.end().y(), WIRE_COLOUR, 0.10, 1.0, -1, 0);
		}
		for (double progress : new double[] { 0.0, 0.35, 0.65, 0.85 }) {
			ScreenPoint left = f.projection.approach(f.cam.left(), progress, f.cam);
			ScreenPoint right = f.projection.approach(f.cam.right(), progress, f.cam);
			out.line(Role.FIELD_GUIDE, left.x(), left.y(), right.x(), right.y(), WIRE_COLOUR, 0.12, 1.0, -1, 0);
		}
	}

	/** The strings the active event targets (FR-068): the written strings of the active note or chord, none without an active event. */
	private java.util.Set<Integer> litStrings(int active) {
		java.util.Set<Integer> lit = new java.util.TreeSet<Integer>();
		if (active >= 0) {
			for (ArcadeRenderEvent.Position position : this.events.get(active).positions()) {
				lit.add(position.tgString());
			}
		}
		return lit;
	}

	/** The frets of the fretted positions of the active event (FR-071: the ruler highlights them, FR-072: the band lights their lane). */
	private java.util.Set<Integer> activeFrets(int active) {
		java.util.Set<Integer> frets = new java.util.TreeSet<Integer>();
		if (active >= 0) {
			for (ArcadeRenderEvent.Position position : this.events.get(active).positions()) {
				if (position.fret() > 0) {
					frets.add(position.fret());
				}
			}
		}
		return frets;
	}

	private void drawNeck(Out out, Frame f, Viewport viewport, java.util.Set<Integer> lit) {
		IsometricProjection projection = f.projection;
		Camera cam = f.cam;
		int count = this.neck.layout().count();
		drawField(out, f);
		double w = viewport.width();
		double h = viewport.height();
		// the top edge of the neck is where the lanes end
		double top = f.grid.neckTopY();
		double bottom = h * IsometricProjection.NECK_NEAR_EDGE;
		out.quad(Role.NECK_BODY, new double[] { 0, top, w, top, w, bottom, 0, bottom }, NECK_COLOUR, NECK_ALPHA, -1, 0);
		double wireTop = projection.stringY(0, count) - h * 0.012;
		double wireBottom = projection.stringY(count - 1, count) + h * 0.012;
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
					if (f.activeFrets.contains(n)) {
						out.text(Role.FRET_RULER_ACTIVE, Integer.toString(n), centre, h * 0.905, h * 0.029, RULER_ACTIVE_COLOUR, 1.0, -1, 0);
					} else {
						out.text(Role.FRET_NUMBER, Integer.toString(n), centre, h * 0.905, h * 0.022, WIRE_COLOUR, 1.0, -1, 0);
					}
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
			double width = 1.0 + this.neck.gauge(row) * 1.2;
			if (lit.contains(tgString)) {
				// the string of the active note or chord: bright, with a glow along its length (FR-068); the glow is decoration, the bright colour is not
				if (f.tier.glow()) {
					out.line(Role.GLOW, 0, y, w, y, colour, 0.22, width * 2.2, -1, tgString);
				}
				out.line(Role.STRING, 0, y, w, y, colour, 1.0, width * 1.5, -1, tgString);
			} else {
				out.line(Role.STRING, 0, y, w, y, colour, STRING_DIM_ALPHA, width, -1, tgString);
			}
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
		java.util.Set<Integer> activeFrets = new java.util.TreeSet<Integer>();
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
		/** The notes whose fret number would touch another label: the marker is drawn, the number is left out. */
		final java.util.Set<Integer> noLabel = new java.util.HashSet<Integer>();
		/** The chords whose name would touch another label: the panel is drawn, the name is left out (the active chord always has it). */
		final java.util.Set<Integer> noName = new java.util.HashSet<Integer>();
	}

	/** Where a note is on the screen, and how big. */
	private static final class NoteGeo {
		ScreenPoint at;
		/** Half height of the marker: the target's at the onset, in perspective before, times the setting of the size. */
		double halfHeight;
		/** Half width of the marker: the width of its cell at its depth (a higher fret is a narrower cell), times the setting of the size. */
		double halfWidth;
		double x;
		int fret;

		double rx() {
			return this.halfWidth;
		}

		double ry() {
			return this.halfHeight;
		}

		/** How far the marker is from its footprint on the lane: the distance from the top edge of the neck to its string, in perspective (FR-071, Q29). */
		double drop;

		double markerY() {
			return this.at.y() + this.drop;
		}

		double numberSize() {
			return Math.max(8.0, this.halfHeight * 2.2);
		}

		double numberY() {
			return this.at.y() + this.ry() * 0.6 + numberSize() * 0.75;
		}

		/** The box of the fret number under the footprint, where it must overlap no other label. */
		double[] numberBox() {
			double w = numberSize() * 0.66 * Integer.toString(this.fret).length();
			return new double[] { this.at.x() - w / 2.0, numberY() - numberSize() * 0.6, this.at.x() + w / 2.0, numberY() + numberSize() * 0.6 };
		}

		/** The marker alone: what must stay in view when something else is placed near it. */
		double[] markerBox() {
			return new double[] { this.at.x() - rx(), markerY() - ry(), this.at.x() + rx(), markerY() + ry() };
		}

		/** The whole figure, from the top of the marker to the bottom of the number. */
		double[] box() {
			double[] number = numberBox();
			return new double[] { this.at.x() - rx(), Math.min(number[1], markerY() - ry()), this.at.x() + rx(), Math.max(number[3], markerY() + ry()) };
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
		g.fret = position.fret();
		double progress = Math.min(1.0, f.progressOf(event));
		g.halfHeight = Math.max(MARKER_MIN_RADIUS, targetHalfHeight(f) * this.markerSize * (0.35 + 0.65 * g.at.scale()));
		g.halfWidth = Math.max(MARKER_MIN_RADIUS, halfWidthOfCell(f, position.fret(), progress) * this.markerSize);
		// the marker goes down the neck as it comes: at the onset it is on its target, the footprint stays on the lane at the top edge
		double stringY = f.projection.stringY(this.neck.layout().rowOf(position.tgString()), this.neck.layout().count());
		// the foot of the neck (owner's mockups A-corde-rouge and A-corde-violette, Q31): the footprint goes down the middle of its cell, through the
		// see-through neck, and is at its bottom edge at the onset; the marker stands above it, as high as its string is from that edge, so that it
		// is on its target at the onset: the red string, at the top of the neck, has the longest thread, the violet one the shortest
		g.at = footAt(f, position.fret(), progress);
		g.drop = -(f.h * IsometricProjection.NECK_NEAR_EDGE - stringY) * g.at.scale();
		return g;
	}

	/** The footprint of a note of the given fret at the given progress: down its lane to the top edge of the neck, then straight down its cell to the bottom edge. */
	private static ScreenPoint footAt(Frame f, int fret, double progress) {
		double bottom = f.h * IsometricProjection.NECK_NEAR_EDGE;
		double top = f.projection.hitLineY();
		double hor = f.projection.horizonY();
		FretArrivalLaneMap.Lane lane = f.lanes.laneForFret(fret);
		double yf = hor + (bottom - hor) * IsometricProjection.depthShare(Math.max(0.0, Math.min(1.0, progress)));
		if (yf <= top) {
			double v = (yf - hor) / (top - hor);
			return new ScreenPoint(lane.origin().x() + (lane.end().x() - lane.origin().x()) * v, yf, f.projection.farScale() + (1.0 - f.projection.farScale()) * v);
		}
		return new ScreenPoint(lane.end().x(), yf, 1.0);
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
		for (int index : candidates) {
			ArcadeRenderEvent event = this.events.get(index);
			boolean isActive = index == active;
			RenderState state = f.stateOf(event);
			double[] next = nextPlayableFootprint(f, visible, index);
			List<double[]> mine = new ArrayList<double[]>();
			if (event.kind() == ArcadeRenderEvent.Kind.NOTE) {
				// the marker is always drawn; its fret number only where it touches no label already placed (the nearest notes are placed first)
				double[] box = isOpenNote(event) ? openGeo(f, event).labelBox(this.labels.openString(), f.h) : noteGeo(f, event).numberBox();
				boolean free = disjointFromAll(box, taken) && inside(box, f.viewport) && (next == null || disjoint(box, next));
				if (this.densityBudget && !isActive && !free) {
					plan.noLabel.add(index);
				} else {
					mine.add(box);
				}
			} else if (event.kind() == ArcadeRenderEvent.Kind.CHORD) {
				// every chord of the set has its panel; its name only where it touches no label already placed (the active chord always has it)
				double[] box = panelGeo(f, event).nameBox(headerText(event) + (this.cartouches[index].stroke() != ArcadeRenderEvent.Stroke.NONE ? "  \u2193" : ""), f.viewport.width());
				boolean free = disjointFromAll(box, taken) && inside(box, f.viewport);
				if (this.densityBudget && !isActive && !free) {
					plan.noName.add(index);
				} else {
					mine.add(box);
				}
			} else {
				ChordGeo g = chordGeo(f, event);
				String word = event.kind() == ArcadeRenderEvent.Kind.AUTOMATIC ? this.labels.automatic() : this.labels.unsupported();
				mine.add(textBox(word, g.centreX, g.barTop - f.h * 0.025, f.h * 0.026));
			}
			if (this.densityBudget && !isActive && event.kind() != ArcadeRenderEvent.Kind.NOTE && event.kind() != ArcadeRenderEvent.Kind.CHORD) {
				boolean free = true;
				for (double[] box : mine) {
					free &= disjointFromAll(box, taken) && inside(box, f.viewport) && (next == null || disjoint(box, next));
				}
				if (!free) {
					// the word of an automatic event that would touch another label is left out with the event: it is passed by itself
					continue;
				}
			}
			plan.detailed.add(index);
			taken.addAll(mine);
		}
		return plan;
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
				return event.kind() == ArcadeRenderEvent.Kind.NOTE ? noteGeo(f, event).markerBox() : null;
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
	private void drawDetailed(Frame f, int index, boolean active, Plan plan) {
		ArcadeRenderEvent event = this.events.get(index);
		RenderState state = f.stateOf(event);
		double alpha = alphaOf(state, f.now, event.onsetNanos(), f.settings);
		if (event.kind() == ArcadeRenderEvent.Kind.NOTE) {
			drawNote(f, event, state, alpha, active);
		} else if (event.kind() == ArcadeRenderEvent.Kind.CHORD) {
			drawChordPanel(f, event, state, alpha, active, plan);
		} else {
			drawBarEvent(f, event, state, alpha);
		}
	}

	/** A single note written at fret 0: the open string, drawn as a bar across the lanes (FR-069). */
	private static boolean isOpenNote(ArcadeRenderEvent event) {
		return event.kind() == ArcadeRenderEvent.Kind.NOTE && event.positions().get(0).fret() == 0;
	}

	/** Where the bar of an open string is: the width of the lanes at its depth, its lower edge on the line of the lanes, a thickness growing with perspective. */
	private final class OpenGeo {
		double left;
		double right;
		double bottom;
		double thickness;

		double[] labelBox(String word, double h) {
			double size = Math.max(9.0, h * 0.024);
			return textBox(word, (this.left + this.right) / 2.0, this.bottom - this.thickness - size / 2.0, size);
		}
	}

	private OpenGeo openGeo(Frame f, ArcadeRenderEvent event) {
		double progress = Math.min(1.0, f.progressOf(event));
		ScreenPoint left = f.projection.approach(f.cam.left(), progress, f.cam);
		ScreenPoint right = f.projection.approach(f.cam.right(), progress, f.cam);
		OpenGeo g = new OpenGeo();
		g.left = left.x();
		g.right = right.x();
		g.bottom = left.y();
		g.thickness = Math.max(3.0, f.h * 0.014 * left.scale());
		return g;
	}

	private void drawOpenBar(Frame f, ArcadeRenderEvent event, double alpha) {
		Out out = f.out;
		int tgString = event.positions().get(0).tgString();
		int colour = this.colours.of(tgString);
		OpenGeo g = openGeo(f, event);
		double top = g.bottom - g.thickness;
		if (f.tier.glow()) {
			out.quad(Role.GLOW, new double[] { g.left, top - g.thickness, g.right, top - g.thickness, g.right, g.bottom + g.thickness * 0.5, g.left, g.bottom + g.thickness * 0.5 },
					colour, alpha * 0.20, event.index(), tgString);
		}
		out.quad(Role.OPEN_BAR, new double[] { g.left, top, g.right, top, g.right, g.bottom, g.left, g.bottom }, colour, alpha, event.index(), tgString);
		double cap = Math.max(3.0, g.thickness * 0.9);
		out.quad(Role.OPEN_CAP, new double[] { g.left, top - g.thickness * 2.0, g.left + cap, top - g.thickness * 2.0, g.left + cap, g.bottom, g.left, g.bottom }, 0xFFFFFF, alpha,
				event.index(), tgString);
		// only the near bars are named: a label on each far bar makes the top of the screen busy
		if (f.progressOf(event) >= 0.7 && (f.noLabel == null || !f.noLabel.contains(event.index()))) {
			double size = Math.max(9.0, f.h * 0.024);
			out.text(Role.OPEN_LABEL, this.labels.openString(), (g.left + g.right) / 2.0, g.bottom - g.thickness - size / 2.0, size, TEXT_COLOUR, alpha, event.index(), tgString);
		}
	}

	private void drawNote(Frame f, ArcadeRenderEvent event, RenderState state, double alpha, boolean active) {
		Out out = f.out;
		ArcadeRenderEvent.Position position = event.positions().get(0);
		int count = this.neck.layout().count();
		int row = this.neck.layout().rowOf(position.tgString());
		int colour = this.colours.of(position.tgString());
		// a note that has its verdict is gone from the lane: the badge says the rest, and the next note of the same fret, which comes on the same lane, finds it free
		if (isJudged(f, event.index(), state)) {
			return;
		}
		if (isOpenNote(event)) {
			drawOpenBar(f, event, alpha);
			return;
		}
		NoteGeo g = noteGeo(f, event);
		FretArrivalLaneMap.Lane lane = f.lanes.laneForFret(position.fret());
		ScreenPoint far = lane.origin();
		// the trajectory of a note is its lane, the one of its fret (FR-066): from the horizon to the middle of the top edge of its cell, where it stops; nothing
		// continues across the neck to the string, the target disk of the neck tells the string
		ScreenPoint end = lane.end();
		out.line(Role.TRAJECTORY, far.x(), far.y(), end.x(), end.y(), colour, alpha * 0.3, 1.0, event.index(), position.tgString());
		if (f.tier.trails() && active) {
			ScreenPoint behind = footAt(f, position.fret(), Math.max(0.0, f.progressOf(event) - 0.12));
			out.line(Role.TRAIL, behind.x(), behind.y(), g.at.x(), g.at.y(), colour, alpha * 0.30, 1.0, event.index(), position.tgString());
		}
		double my = g.markerY();
		// only the active note glows: a halo on every marker makes the picture heavy and blurred
		if (f.tier.glow() && active) {
			out.rounded(Role.GLOW, g.at.x(), my, g.rx() * 1.25, g.ry() * 1.45, colour, alpha * 0.18, event.index(), position.tgString());
		}
		// the marker comes down the neck towards its target, joined by a thin thread to its footprint on the lane (FR-071)
		out.line(Role.MARKER_THREAD, g.at.x(), g.at.y(), g.at.x(), Math.min(g.at.y(), my + g.ry()), colour, alpha * 0.7, 1.0, event.index(), position.tgString());
		out.rounded(Role.MARKER_FOOTPRINT, g.at.x(), g.at.y(), g.rx(), Math.max(0.8, g.ry() * 0.16), colour, alpha * 0.5, event.index(), position.tgString());
		out.rounded(Role.MARKER, g.at.x(), my, g.rx(), g.ry(), colour, alpha, event.index(), position.tgString());
		// the number of the fret is written under the footprint, never in the marker; it is left out where it would overlap a label, or once the footprint is in the
		// neck: the ruler of the frets under the neck then says the fret of the active note itself
		if (g.at.y() < f.projection.hitLineY() && (f.noLabel == null || !f.noLabel.contains(event.index()))) {
			out.text(Role.MARKER_FRET, Integer.toString(position.fret()), g.at.x(), g.numberY(), g.numberSize(), RULER_ACTIVE_COLOUR, alpha, event.index(), position.tgString());
		}
	}

	/** An event the learner does not play (passed automatically, or not supported): a timing bar with its word. */
	private void drawBarEvent(Frame f, ArcadeRenderEvent event, RenderState state, double alpha) {
		Out out = f.out;
		ChordGeo g = chordGeo(f, event);
		out.quad(Role.TIMING_BAR, new double[] { g.pl.x(), g.pl.y(), g.pr.x(), g.pr.y(), g.pr.x(), g.pr.y() - g.thickness, g.pl.x(), g.pl.y() - g.thickness },
				BAR_COLOUR, alpha, event.index(), 0);
		String word = event.kind() == ArcadeRenderEvent.Kind.AUTOMATIC ? this.labels.automatic() : this.labels.unsupported();
		out.text(Role.EVENT_TEXT, word, g.centreX, g.barTop - f.h * 0.025, f.h * 0.026, TEXT_COLOUR, alpha, event.index(), 0);
	}

	/** Where the panel of a chord is at its depth: the width of the lanes, its lower edge on the line of the lanes, its height growing with perspective (FR-074). */
	private final class PanelGeo {
		double left;
		double right;
		double bottom;
		double top;
		double scale;
		double progress;

		double height() {
			return this.bottom - this.top;
		}

		double nameSize() {
			return Math.max(10.0, height() * 0.26);
		}

		/** The room of the name above the left corner (and of the arrow of the stroke): it must overlap no other label. */
		/** The middle y of the name: above the top edge of the panel, kept inside the window when the panel is high on the screen (far away). */
		double nameY() {
			double size = nameSize();
			return Math.max(this.top - size * 0.75, size * 0.65 + 1.0);
		}

		double[] nameBox(String text, double viewportWidth) {
			double size = nameSize();
			double width = text.length() * size * 0.66;
			double y = nameY();
			double x = nameX(width, viewportWidth);
			return new double[] { x, y - size * 0.6, x + width, y + size * 0.6 };
		}

		/** The name starts at the left corner of the panel, moved left only as far as it takes to stay whole inside the window. */
		double nameX(double width, double viewportWidth) {
			return Math.max(2.0, Math.min(this.left, viewportWidth - width - 2.0));
		}
	}

	private PanelGeo panelGeo(Frame f, ArcadeRenderEvent event) {
		double progress = Math.min(1.0, f.progressOf(event));
		// the panel spans the cells of the neck from the first fret of the chord to its last, inclusive, so that it lands exactly on the cells that are played (FR-074)
		int first = Integer.MAX_VALUE;
		int last = 0;
		for (ArcadeRenderEvent.Position position : event.positions()) {
			if (position.fret() > 0) {
				first = Math.min(first, position.fret());
				last = Math.max(last, position.fret());
			}
		}
		ScreenPoint left = last == 0 ? f.projection.approach(f.cam.left(), progress, f.cam) : f.projection.approach(this.neck.fretX(first - 1), progress, f.cam);
		ScreenPoint right = last == 0 ? f.projection.approach(f.cam.right(), progress, f.cam) : f.projection.approach(this.neck.fretX(last), progress, f.cam);
		PanelGeo g = new PanelGeo();
		g.left = left.x();
		g.right = right.x();
		g.bottom = left.y();
		g.scale = left.scale();
		g.top = g.bottom - Math.max(24.0, f.h * 0.30 * left.scale());
		g.progress = progress;
		return g;
	}

	/**
	 * A chord (FR-074): a translucent panel across the lanes with luminous edges; inside it a rounded rectangle for every fretted position, at its fret
	 * (horizontally, as the frets of the neck at this depth) and its string (vertically, the lowest string at the top), a line across for an open string;
	 * the fret on the rectangles of the active chord; the name above the left corner.
	 */
	private void drawChordPanel(Frame f, ArcadeRenderEvent event, RenderState state, double alpha, boolean active, Plan plan) {
		Out out = f.out;
		PanelGeo g = panelGeo(f, event);
		int index = event.index();
		int count = this.neck.layout().count();
		out.quad(Role.CHORD_PANEL, new double[] { g.left, g.top, g.right, g.top, g.right, g.bottom, g.left, g.bottom }, PANEL_BODY_COLOUR, alpha * 0.14, index, 0);
		double[][] edge = { { g.left, g.top, g.right, g.top }, { g.right, g.top, g.right, g.bottom }, { g.right, g.bottom, g.left, g.bottom }, { g.left, g.bottom, g.left, g.top } };
		for (double[] e : edge) {
			if (f.tier.glow()) {
				out.line(Role.GLOW, e[0], e[1], e[2], e[3], PANEL_EDGE_COLOUR, alpha * 0.22, 7.0, index, 0);
			}
			out.line(Role.CHORD_PANEL_EDGE, e[0], e[1], e[2], e[3], PANEL_EDGE_COLOUR, alpha * 0.9, 2.0, index, 0);
		}
		double rowHeight = g.height() / count;
		double ry = Math.max(1.5, rowHeight * 0.34);
		for (ArcadeRenderEvent.Position position : event.positions()) {
			int row = this.neck.layout().rowOf(position.tgString());
			double y = g.top + rowHeight * (row + 0.5);
			int colour = this.colours.of(position.tgString());
			if (position.fret() == 0) {
				out.line(Role.PANEL_OPEN_LINE, g.left, y, g.right, y, colour, alpha, Math.max(1.5, ry * 0.5), index, position.tgString());
				continue;
			}
			double x = f.projection.approach(this.neck.fretCentreX(position.fret()), g.progress, f.cam).x();
			double cell = Math.abs(f.projection.approach(this.neck.fretX(position.fret()), g.progress, f.cam).x() - f.projection.approach(this.neck.fretX(position.fret() - 1), g.progress, f.cam).x());
			double rx = Math.max(1.5, cell * 0.44);
			if (f.tier.glow()) {
				out.rounded(Role.GLOW, x, y, rx * 1.3, ry * 1.5, colour, alpha * 0.25, index, position.tgString());
			}
			out.rounded(Role.PANEL_NOTE, x, y, rx, ry, colour, alpha, index, position.tgString());
			if (active) {
				out.text(Role.PANEL_FRET, Integer.toString(position.fret()), x, y, Math.max(8.0, ry * 1.5), PANEL_FRET_COLOUR, alpha, index, position.tgString());
			}
		}
		if (active || !plan.noName.contains(index)) {
			String name = headerText(event);
			double size = g.nameSize();
			double y = g.nameY();
			double nameX = g.nameX(name.length() * size * 0.66 + (this.cartouches[index].stroke() != ArcadeRenderEvent.Stroke.NONE ? size * 1.9 : 0.0), f.viewport.width());
			out.text(Role.PANEL_NAME, name, nameX, y, size, TEXT_COLOUR, Math.max(alpha, 0.9), index, 0, SceneItem.Align.LEFT);
			ChordCartouche cartouche = this.cartouches[index];
			if (cartouche.stroke() != ArcadeRenderEvent.Stroke.NONE) {
				out.text(Role.PANEL_STROKE, cartouche.stroke() == ArcadeRenderEvent.Stroke.DOWN ? "\u2193" : "\u2191", nameX + (name.length() + 1) * size * 0.66, y, size, TEXT_COLOUR,
						Math.max(alpha, 0.9), index, 0, SceneItem.Align.LEFT);
			}
			if (cartouche.notCheckedSeparately() && state.ordinal() >= RenderState.READABLE.ordinal()) {
				out.text(Role.PANEL_REMARK, this.labels.notCheckedSeparately(), (g.left + g.right) / 2.0, g.bottom + 10.0, 10.0, TEXT_COLOUR, alpha, index, 0);
			}
		}
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

		void rounded(Role role, double cx, double cy, double rx, double ry, int colour, double alpha, int event, int string) {
			this.items.add(new SceneItem(Shape.ROUNDED, role, new double[] { cx, cy, rx, ry }, colour(colour), alpha, 0.0, null, event, string, null));
		}

		void roundedRing(Role role, double cx, double cy, double rx, double ry, int colour, double alpha, double width, int event, int string) {
			this.items.add(new SceneItem(Shape.ROUNDED_RING, role, new double[] { cx, cy, rx, ry }, colour(colour), alpha, width, null, event, string, null));
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
