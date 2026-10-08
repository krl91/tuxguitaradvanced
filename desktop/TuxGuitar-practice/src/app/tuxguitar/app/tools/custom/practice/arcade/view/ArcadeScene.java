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
	 * @param nowNanos the clock reading of the player
	 * @param run the state of the run, or null before the first snapshot
	 * @param speedPercent the speed of the run, for the HUD
	 */
	public Scene build(long nowNanos, Viewport viewport, ArcadeRun.View run, int speedPercent) {
		Out out = new Out(this.monochrome);
		IsometricProjection projection = new IsometricProjection(viewport);
		Camera cam = this.camera.at(nowNanos);
		CameraTrack.Settings settings = this.camera.settings();
		drawNeck(out, projection, cam, viewport);

		List<Integer> visible = visibleEvents(nowNanos, settings);
		for (int k = visible.size() - 1; k >= 0; k--) {
			drawEvent(out, projection, cam, viewport, visible.get(k), nowNanos, settings);
		}
		drawBadges(out, projection, cam, viewport, visible, run, nowNanos, settings);
		drawHud(out, viewport, run, speedPercent);
		return new Scene(out.items, nowNanos, viewport);
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

	private void drawNeck(Out out, IsometricProjection projection, Camera cam, Viewport viewport) {
		int count = this.neck.layout().count();
		double w = viewport.width();
		double h = viewport.height();
		double top = h * (IsometricProjection.HIT_LINE + 0.02);
		double bottom = h * IsometricProjection.NECK_NEAR_EDGE;
		out.quad(Role.NECK_BODY, new double[] { 0, top, w, top, w, bottom, 0, bottom }, NECK_COLOUR, 1.0, -1, 0);
		double wireTop = projection.stringY(count - 1, count) - h * 0.012;
		double wireBottom = projection.stringY(0, count) + h * 0.012;
		for (int n = 0; n <= this.neck.fretCount(); n++) {
			double x = this.neck.fretX(n);
			if (!cam.shows(x, 0.0)) {
				continue;
			}
			double px = projection.neckX(x, cam);
			out.line(n == 0 ? Role.NUT : Role.FRET_WIRE, px, wireTop, px, wireBottom, WIRE_COLOUR, 1.0, n == 0 ? 4.0 : 1.5, -1, 0);
			if (n >= 1) {
				double centre = projection.neckX(this.neck.fretCentreX(n), cam);
				out.text(Role.FRET_NUMBER, Integer.toString(n), centre, h * 0.905, h * 0.022, WIRE_COLOUR, 1.0, -1, 0);
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
			out.text(Role.STRING_LABEL, PitchNames.of(this.neck.openMidi(row)), w * 0.028, y, h * 0.026, colour, 1.0, -1, tgString);
		}
		out.line(Role.HIT_LINE, 0, projection.hitLineY(), w, projection.hitLineY(), HIT_COLOUR, 1.0, 2.0, -1, 0);
	}

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

	private void drawEvent(Out out, IsometricProjection projection, Camera cam, Viewport viewport, int index, long nowNanos, CameraTrack.Settings settings) {
		ArcadeRenderEvent event = this.events.get(index);
		RenderState state = RenderState.of(event.onsetNanos(), nowNanos, settings.lookAheadNanos, settings.holdAfterNanos);
		double progress = RenderState.progress(event.onsetNanos(), nowNanos, settings.lookAheadNanos);
		double alpha = alphaOf(state, nowNanos, event.onsetNanos(), settings);
		if (event.kind() == ArcadeRenderEvent.Kind.NOTE) {
			drawNote(out, projection, cam, viewport, event, state, progress, alpha);
		} else {
			drawBarEvent(out, projection, cam, viewport, event, state, progress, alpha);
		}
	}

	private void drawNote(Out out, IsometricProjection projection, Camera cam, Viewport viewport, ArcadeRenderEvent event, RenderState state, double progress,
			double alpha) {
		ArcadeRenderEvent.Position position = event.positions().get(0);
		int count = this.neck.layout().count();
		int row = this.neck.layout().rowOf(position.tgString());
		int colour = this.colours.of(position.tgString());
		double x = this.neck.fretCentreX(position.fret());
		double h = viewport.height();
		ScreenPoint far = projection.approach(x, 0.0, cam);
		ScreenPoint now = projection.approach(x, progress, cam);
		ScreenPoint target = projection.neck(x, row, count, cam);
		out.line(Role.TRAJECTORY, far.x(), far.y(), target.x(), target.y(), colour, alpha * 0.6, 2.0, event.index(), position.tgString());
		double ring = h * 0.026;
		out.ring(Role.TARGET_RING, target.x(), target.y(), ring, ring, colour, alpha, 3.0, event.index(), position.tgString());
		if (state.ordinal() >= RenderState.READABLE.ordinal() && state != RenderState.EXPIRED) {
			out.ring(Role.GHOST, target.x(), target.y(), ring * 1.5, ring * 1.5, colour, alpha * 0.5, 1.5, event.index(), position.tgString());
		}
		double radius = Math.max(6.0, h * 0.030 * now.scale());
		out.disc(Role.MARKER, now.x(), now.y(), radius, radius, colour, alpha, event.index(), position.tgString());
		out.text(Role.MARKER_FRET, Integer.toString(position.fret()), now.x(), now.y(), Math.max(8.0, radius * 1.1), 0xFFFFFF, alpha, event.index(),
				position.tgString());
	}

	private void drawBarEvent(Out out, IsometricProjection projection, Camera cam, Viewport viewport, ArcadeRenderEvent event, RenderState state,
			double progress, double alpha) {
		double h = viewport.height();
		double left;
		double right;
		if (event.positions().isEmpty()) {
			left = cam.left();
			right = cam.right();
		} else {
			double min = Double.MAX_VALUE;
			double max = -Double.MAX_VALUE;
			for (ArcadeRenderEvent.Position position : event.positions()) {
				double x = this.neck.fretCentreX(position.fret());
				min = Math.min(min, x);
				max = Math.max(max, x);
			}
			left = min - 8.0;
			right = max + 8.0;
		}
		ScreenPoint pl = projection.approach(left, progress, cam);
		ScreenPoint pr = projection.approach(right, progress, cam);
		double thickness = Math.max(3.0, h * 0.014 * pl.scale());
		out.quad(Role.TIMING_BAR, new double[] { pl.x(), pl.y(), pr.x(), pr.y(), pr.x(), pr.y() - thickness, pl.x(), pl.y() - thickness }, BAR_COLOUR, alpha,
				event.index(), 0);
		double centreX = (pl.x() + pr.x()) / 2.0;
		double barTop = pl.y() - thickness;
		if (event.kind() != ArcadeRenderEvent.Kind.CHORD) {
			String word = event.kind() == ArcadeRenderEvent.Kind.AUTOMATIC ? this.labels.automatic() : this.labels.unsupported();
			out.text(Role.EVENT_TEXT, word, centreX, barTop - h * 0.025, h * 0.026, TEXT_COLOUR, alpha, event.index(), 0);
			return;
		}
		ChordCartouche cartouche = this.cartouches[event.index()];
		double scale = pl.scale();
		double unit = h / 720.0;
		double boxW = Math.max(ChordCartouche.MIN_WIDTH_PX, (20 + 18 * (cartouche.strings() - 1)) * unit * scale);
		double boxH = Math.max(ChordCartouche.MIN_HEIGHT_PX, (24 + 14 * cartouche.rows()) * unit * 1.6 * scale);
		double stem = Math.max(6.0, h * 0.03 * scale);
		double boxX = Math.max(2.0, Math.min(viewport.width() - boxW - 2.0, centreX - boxW / 2.0));
		double boxBottom = barTop - stem;
		double boxY = Math.max(cartouche.notCheckedSeparately() ? 16.0 : 2.0, boxBottom - boxH);
		out.line(Role.STEM, centreX, barTop, centreX, boxY + boxH, BAR_COLOUR, alpha, 2.0, event.index(), 0);
		drawCartouche(out, cartouche, event, state, boxX, boxY, boxW, boxH, alpha);
		if (state.ordinal() >= RenderState.READABLE.ordinal()) {
			int count = this.neck.layout().count();
			for (ArcadeRenderEvent.Position position : event.positions()) {
				ScreenPoint target = projection.neck(this.neck.fretCentreX(position.fret()), this.neck.layout().rowOf(position.tgString()), count, cam);
				double ring = h * 0.026;
				out.ring(Role.GHOST, target.x(), target.y(), ring, ring, this.colours.of(position.tgString()), alpha * 0.5, 1.5, event.index(),
						position.tgString());
			}
		}
	}

	private void drawCartouche(Out out, ChordCartouche cartouche, ArcadeRenderEvent event, RenderState state, double x, double y, double w, double h,
			double alpha) {
		int strings = cartouche.strings();
		out.quad(Role.CARTOUCHE_FRAME, new double[] { x, y, x + w, y, x + w, y + h, x, y + h }, FRAME_COLOUR, alpha, event.index(), 0);
		double nameBand = h * 0.22;
		double markBand = h * 0.12;
		double gridTop = y + nameBand + markBand;
		double gridBottom = y + h - h * 0.04;
		double gridLeft = x + w * 0.26;
		double gridRight = x + w - w * 0.10;
		double columnGap = strings == 1 ? 0.0 : (gridRight - gridLeft) / (strings - 1);
		double rowHeight = (gridBottom - gridTop) / cartouche.rows();
		String name = cartouche.name() != null ? cartouche.name() : this.labels.chordFallback();
		double nameSize = Math.max(8.0, nameBand * 0.62);
		out.text(Role.CARTOUCHE_NAME, name, x + w * 0.45, y + nameBand / 2.0, nameSize, TEXT_COLOUR, alpha, event.index(), 0);
		if (cartouche.stroke() != ArcadeRenderEvent.Stroke.NONE) {
			out.text(Role.CARTOUCHE_STROKE, cartouche.stroke() == ArcadeRenderEvent.Stroke.DOWN ? "↓" : "↑", x + w * 0.88, y + nameBand / 2.0, nameSize,
					TEXT_COLOUR, alpha, event.index(), 0);
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

	private void drawBadges(Out out, IsometricProjection projection, Camera cam, Viewport viewport, List<Integer> visible, ArcadeRun.View run, long nowNanos,
			CameraTrack.Settings settings) {
		if (run == null) {
			return;
		}
		double h = viewport.height();
		double badgeH = h * 0.036;
		List<double[]> placed = new ArrayList<double[]>();
		for (int index : visible) {
			if (index >= run.states().size()) {
				continue;
			}
			ArcadeRenderEvent event = this.events.get(index);
			RenderState state = RenderState.of(event.onsetNanos(), nowNanos, settings.lookAheadNanos, settings.holdAfterNanos);
			ArcadeRun.EventState result = run.states().get(index);
			if (result.verdict() == ArcadeVerdict.WAITING || state.ordinal() < RenderState.ARMED.ordinal()) {
				continue;
			}
			String names = missingNames(result);
			double badgeW = viewport.width() * (names.isEmpty() ? 0.15 : 0.22);
			double column = projection.neckX(columnOf(event), cam);
			double bx = Math.max(2.0, Math.min(viewport.width() - badgeW - 2.0, column - badgeW / 2.0));
			double by = h * 0.92;
			boolean moved = true;
			for (int attempt = 0; attempt < 2 && moved; attempt++) {
				moved = false;
				for (double[] other : placed) {
					if (bx < other[0] + other[2] && other[0] < bx + badgeW && by < other[1] + other[3] && other[1] < by + badgeH) {
						by = Math.min(h - badgeH - 2.0, other[1] + other[3] + 2.0);
						moved = true;
					}
				}
			}
			placed.add(new double[] { bx, by, badgeW, badgeH });
			double alpha = alphaOf(state, nowNanos, event.onsetNanos(), settings);
			int colour = verdictColour(result.verdict());
			out.quad(Role.BADGE, new double[] { bx, by, bx + badgeW, by, bx + badgeW, by + badgeH, bx, by + badgeH }, colour, alpha, index, 0);
			out.text(Role.BADGE_SYMBOL, verdictSymbol(result.verdict()), bx + badgeH * 0.6, by + badgeH / 2.0, badgeH * 0.7, 0x101216, alpha, index, 0);
			String word = this.labels.verdict(result.verdict());
			out.text(Role.BADGE_TEXT, names.isEmpty() ? word : word + " · " + this.labels.missing(names), bx + badgeW / 2.0 + badgeH * 0.3, by + badgeH / 2.0,
					badgeH * 0.55, 0x101216, alpha, index, 0);
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

	private void drawHud(Out out, Viewport viewport, ArcadeRun.View run, int speedPercent) {
		double h = viewport.height();
		if (run != null) {
			out.text(Role.HUD_STREAK, this.labels.streak(run.streak()), viewport.width() * 0.03, h * 0.05, h * 0.034, TEXT_COLOUR, 1.0, -1, 0, false);
		}
		out.text(Role.HUD_SPEED, this.labels.speed(speedPercent), viewport.width() * 0.97, h * 0.05, h * 0.034, TEXT_COLOUR, 1.0, -1, 0, false);
	}

	/** Collects the items, removing the colour when the scene is monochrome. */
	private static final class Out {
		final List<SceneItem> items = new ArrayList<SceneItem>();
		private final boolean monochrome;

		Out(boolean monochrome) {
			this.monochrome = monochrome;
		}

		private int colour(int rgb) {
			if (!this.monochrome) {
				return rgb;
			}
			int grey = (int) Math.round(0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF));
			return (grey << 16) | (grey << 8) | grey;
		}

		void line(Role role, double x1, double y1, double x2, double y2, int colour, double alpha, double width, int event, int string) {
			this.items.add(new SceneItem(Shape.LINE, role, new double[] { x1, y1, x2, y2 }, colour(colour), alpha, width, null, event, string, false));
		}

		void quad(Role role, double[] corners, int colour, double alpha, int event, int string) {
			this.items.add(new SceneItem(Shape.QUAD, role, corners, colour(colour), alpha, 0.0, null, event, string, false));
		}

		void disc(Role role, double cx, double cy, double rx, double ry, int colour, double alpha, int event, int string) {
			this.items.add(new SceneItem(Shape.DISC, role, new double[] { cx, cy, rx, ry }, colour(colour), alpha, 0.0, null, event, string, false));
		}

		void ring(Role role, double cx, double cy, double rx, double ry, int colour, double alpha, double width, int event, int string) {
			this.items.add(new SceneItem(Shape.RING, role, new double[] { cx, cy, rx, ry }, colour(colour), alpha, width, null, event, string, false));
		}

		void text(Role role, String text, double x, double y, double size, int colour, double alpha, int event, int string) {
			text(role, text, x, y, size, colour, alpha, event, string, true);
		}

		void text(Role role, String text, double x, double y, double size, int colour, double alpha, int event, int string, boolean centred) {
			this.items.add(new SceneItem(Shape.TEXT, role, new double[] { x, y, size }, colour(colour), alpha, 0.0, text, event, string, centred));
		}
	}
}
