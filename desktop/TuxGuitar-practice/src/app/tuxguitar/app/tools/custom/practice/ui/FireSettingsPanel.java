package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FireParameter;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;
import app.tuxguitar.app.tools.custom.practice.session.FireTuningStore;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UIScrollBarPanelLayout;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIColorModel;
import app.tuxguitar.ui.resource.UIImage;
import app.tuxguitar.ui.resource.UIPainter;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.chooser.UIColorChooser;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UILegendPanel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UIScrollBarPanel;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * The Advanced tab of the practice window (spec 019 FR-014, FR-015, FR-018, FR-019, FR-020, contracts/advanced-tab.md): the groups *Arcade fire* and
 * *Score fire*, each with its live preview, a *Sparks* button, a growth slider, *Default values*, and one row per setting of its group by section. A number
 * or integer row is a label, a scale and the value with its unit; a colour row is a label, a swatch button opening the colour chooser and the value. The
 * label and the control carry the setting's tooltip, its unit and its default. A move is brought within the range, kept at once (one value written) and
 * drawn by the preview at its next picture; a run reads the stored values when it starts, so a run going on is not changed. Toolkit only. UI thread only.
 */
final class FireSettingsPanel {

	/** Translated texts ({@code TuxGuitar.getProperty} in the application). */
	interface Messages {
		String get(String key);

		String get(String key, String[] arguments);
	}

	/** Positions of the scale of a number: the range in a thousand steps. */
	static final int NUMBER_STEPS = 1000;

	/** Positions of the growth slider. */
	static final int GROWTH_STEPS = 100;

	/** Width of the values: long enough for "1.33 heights/s". */
	static final float VALUE_WIDTH = 120f;

	static final float SCALE_WIDTH = 220f;

	/** Size of the swatch of a colour. */
	static final int SWATCH = 14;

	/** One row of the tab. */
	private static final class Row {
		UILabel label;
		UIControl control;
		UILabel value;
		UIImage swatch;
	}

	private final UIFactory factory;
	private final UIWindow window;
	private final Messages messages;
	private final FireTuningStore store;
	private final UIScrollBarPanel scroll;
	private final Map<FireTuning.Group, FireTuning> tunings = new EnumMap<FireTuning.Group, FireTuning>(FireTuning.Group.class);
	private final Map<FireTuning.Group, FirePreview> previews = new EnumMap<FireTuning.Group, FirePreview>(FireTuning.Group.class);
	private final Map<FireTuning.Group, UIButton> defaultsButtons = new EnumMap<FireTuning.Group, UIButton>(FireTuning.Group.class);
	private final Map<FireTuning.Group, Map<FireParameter, Row>> rows = new EnumMap<FireTuning.Group, Map<FireParameter, Row>>(FireTuning.Group.class);
	private boolean updating;

	/**
	 * @param window the window the colour chooser opens over
	 * @param tickers a new timer for each preview
	 * @param motion false when the system asks to reduce motion: the previews are then drawn still
	 */
	FireSettingsPanel(UIFactory factory, UIWindow window, UIContainer parent, Messages messages, FireTuningStore store, Supplier<ScoreFire.Ticker> tickers,
			LongSupplier wall, boolean motion) {
		this.factory = factory;
		this.window = window;
		this.messages = messages;
		this.store = store;
		this.scroll = factory.createScrollBarPanel(parent, true, false, false);
		// the tab scrolls when the window is small, and asks for no height of its own: it adds nothing to the other tabs
		this.scroll.setLayout(new UIScrollBarPanelLayout(false, true, true, false, false, true));
		this.scroll.getVScroll().setIncrement(16);
		this.scroll.getVScroll().addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				FireSettingsPanel.this.scroll.layout();
			}
		});
		UIPanel content = factory.createPanel(this.scroll, false);
		UITableLayout layout = new UITableLayout();
		content.setLayout(layout);
		int row = 1;
		for (FireTuning.Group group : FireTuning.Group.values()) {
			this.tunings.put(group, store.load(group));
			FirePreview preview = new FirePreview(factory, this.tunings.get(group), tickers.get(), wall, System::nanoTime);
			preview.setMotion(motion);
			this.previews.put(group, preview);
			UILegendPanel legend = factory.createLegendPanel(content);
			legend.setText(messages.get(group.labelKey()));
			buildGroup(group, legend);
			layout.set(legend, row++, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_TOP, true, false);
		}
	}

	UIControl getControl() {
		return this.scroll;
	}

	/** The previews move only while the tab is selected and the window is shown. */
	void setShown(boolean shown) {
		for (FirePreview preview : this.previews.values()) {
			preview.setShown(shown);
		}
	}

	/** False when the system asks to reduce motion: the previews are drawn still, without sparks. */
	void setMotion(boolean motion) {
		for (FirePreview preview : this.previews.values()) {
			preview.setMotion(motion);
		}
	}

	void dispose() {
		for (FirePreview preview : this.previews.values()) {
			preview.dispose();
		}
		for (Map<FireParameter, Row> group : this.rows.values()) {
			for (Row row : group.values()) {
				if (row.swatch != null && !row.swatch.isDisposed()) {
					row.swatch.dispose();
				}
			}
		}
	}

	List<FireParameter> getParameters(FireTuning.Group group) {
		return new ArrayList<FireParameter>(this.rows.get(group).keySet());
	}

	UILabel getLabel(FireTuning.Group group, FireParameter parameter) {
		return this.rows.get(group).get(parameter).label;
	}

	UIControl getControl(FireTuning.Group group, FireParameter parameter) {
		return this.rows.get(group).get(parameter).control;
	}

	UILabel getValueLabel(FireTuning.Group group, FireParameter parameter) {
		return this.rows.get(group).get(parameter).value;
	}

	UIButton getDefaultsButton(FireTuning.Group group) {
		return this.defaultsButtons.get(group);
	}

	FirePreview getPreview(FireTuning.Group group) {
		return this.previews.get(group);
	}

	// ---------------------------------------------------------------- building

	private void buildGroup(final FireTuning.Group group, UILegendPanel legend) {
		final FirePreview preview = this.previews.get(group);
		UITableLayout layout = new UITableLayout();
		legend.setLayout(layout);
		int row = 1;

		UIControl canvas = preview.createCanvas(legend);
		layout.set(canvas, row++, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_TOP, false, false, 1, 3, FirePreview.WIDTH, FirePreview.HEIGHT, 0f);

		UIButton sparks = this.factory.createButton(legend);
		sparks.setText(this.messages.get("practice.fire.preview.sparks"));
		sparks.setToolTipText(this.messages.get("practice.fire.preview.sparks.tip"));
		sparks.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				preview.sparks();
			}
		});
		layout.set(sparks, row, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);
		final UIScale growth = this.factory.createHorizontalScale(legend);
		// maximum first, from a zero minimum (RecognitionControls.sliderMaximum)
		growth.setMaximum(GROWTH_STEPS);
		growth.setMinimum(0);
		growth.setIncrement(1);
		int start = group == FireTuning.Group.SCORE ? (int) Math.round(GROWTH_STEPS / FirePreview.SCORE_REACH) : GROWTH_STEPS;
		growth.setValue(start);
		preview.setGrowth(start / (double) GROWTH_STEPS);
		growth.setToolTipText(this.messages.get("practice.fire.preview.growth.tip"));
		growth.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				preview.setGrowth(growth.getValue() / (double) GROWTH_STEPS);
			}
		});
		layout.set(growth, row, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(growth, UITableLayout.PACKED_WIDTH, SCALE_WIDTH);
		UILabel growthLabel = this.factory.createLabel(legend);
		growthLabel.setText(this.messages.get("practice.fire.preview.growth"));
		growthLabel.setToolTipText(this.messages.get("practice.fire.preview.growth.tip"));
		layout.set(growthLabel, row++, 3, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);

		UIButton defaults = this.factory.createButton(legend);
		defaults.setText(this.messages.get("practice.fire.defaults"));
		defaults.setToolTipText(this.messages.get("practice.fire.defaults.tip"));
		defaults.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				restoreDefaults(group);
			}
		});
		layout.set(defaults, row++, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);
		this.defaultsButtons.put(group, defaults);

		Map<FireParameter, Row> groupRows = new java.util.LinkedHashMap<FireParameter, Row>();
		this.rows.put(group, groupRows);
		for (FireParameter.Section section : FireParameter.Section.values()) {
			UILabel title = this.factory.createLabel(legend);
			title.setText(this.messages.get(section.labelKey()));
			layout.set(title, row++, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false, 1, 3);
			for (FireParameter parameter : FireParameter.values()) {
				if (parameter.section() == section && parameter.in(group)) {
					groupRows.put(parameter, buildRow(group, parameter, legend, layout, row++));
				}
			}
		}
	}

	private Row buildRow(final FireTuning.Group group, final FireParameter parameter, UIContainer parent, UITableLayout layout, int line) {
		final Row row = new Row();
		String tip = tooltip(this.messages, parameter, parameter.defaultFor(group));
		row.label = this.factory.createLabel(parent);
		row.label.setText(this.messages.get(parameter.labelKey()));
		row.label.setToolTipText(tip);
		layout.set(row.label, line, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		if (parameter.kind() == FireParameter.Kind.COLOUR) {
			final UIButton button = this.factory.createButton(parent);
			button.setText(this.messages.get("practice.fire.colour.choose"));
			button.setToolTipText(tip);
			button.addSelectionListener(new UISelectionListener() {
				public void onSelect(UISelectionEvent event) {
					chooseColour(group, parameter);
				}
			});
			layout.set(button, line, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
			row.control = button;
		} else {
			final UIScale scale = this.factory.createHorizontalScale(parent);
			scale.setMaximum(positions(parameter));
			scale.setMinimum(0);
			scale.setIncrement(1);
			scale.setToolTipText(tip);
			scale.addSelectionListener(new UISelectionListener() {
				public void onSelect(UISelectionEvent event) {
					if (!FireSettingsPanel.this.updating) {
						change(group, parameter, fromPosition(parameter, scale.getValue()));
					}
				}
			});
			layout.set(scale, line, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
			layout.set(scale, UITableLayout.PACKED_WIDTH, SCALE_WIDTH);
			row.control = scale;
		}
		row.value = this.factory.createLabel(parent);
		row.value.setToolTipText(tip);
		layout.set(row.value, line, 3, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		layout.set(row.value, UITableLayout.PACKED_WIDTH, VALUE_WIDTH);
		show(group, parameter, row);
		return row;
	}

	// ---------------------------------------------------------------- changes

	/** A new value from a row: brought within its range, kept at once, and drawn by the preview. */
	private void change(FireTuning.Group group, FireParameter parameter, double value) {
		FireTuning tuning = this.tunings.get(group).with(parameter, value);
		this.tunings.put(group, tuning);
		this.store.save(tuning, parameter);
		this.previews.get(group).setTuning(tuning);
		Row row = this.rows.get(group).get(parameter);
		if (row != null) {
			show(group, parameter, row);
		}
	}

	private void restoreDefaults(FireTuning.Group group) {
		this.store.reset(group);
		this.tunings.put(group, FireTuning.defaults(group));
		this.previews.get(group).setTuning(this.tunings.get(group));
		for (Map.Entry<FireParameter, Row> entry : this.rows.get(group).entrySet()) {
			show(group, entry.getKey(), entry.getValue());
		}
	}

	private void chooseColour(final FireTuning.Group group, final FireParameter parameter) {
		int colour = this.tunings.get(group).colour(parameter);
		UIColorChooser chooser = this.factory.createColorChooser(this.window);
		chooser.setDefaultModel(new UIColorModel((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF));
		chooser.setText(this.messages.get(parameter.labelKey()));
		chooser.choose(model -> {
			if (model != null) {
				change(group, parameter, (model.getRed() & 0xFF) << 16 | (model.getGreen() & 0xFF) << 8 | (model.getBlue() & 0xFF));
			}
		});
	}

	/** A row shows the value of its group's tuning now: the scale or the swatch, and the value with its unit. */
	private void show(FireTuning.Group group, FireParameter parameter, Row row) {
		double value = this.tunings.get(group).value(parameter);
		this.updating = true;
		try {
			if (row.control instanceof UIScale) {
				((UIScale) row.control).setValue(toPosition(parameter, value));
			} else if (row.control instanceof UIButton) {
				UIImage old = row.swatch;
				row.swatch = swatch((int) value);
				((UIButton) row.control).setImage(row.swatch);
				if (old != null && !old.isDisposed()) {
					old.dispose();
				}
			}
			row.value.setText(shown(this.messages, parameter, value));
		} finally {
			this.updating = false;
		}
	}

	private UIImage swatch(int colour) {
		UIImage image = this.factory.createImage(SWATCH, SWATCH);
		UIPainter painter = image.createPainter();
		UIColor fill = this.factory.createColor((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF);
		painter.setBackground(fill);
		painter.initPath(UIPainter.PATH_FILL);
		painter.addRectangle(0, 0, SWATCH, SWATCH);
		painter.closePath();
		painter.dispose();
		fill.dispose();
		return image;
	}

	// ---------------------------------------------------------------- values, positions and texts

	/** The positions of the scale of a setting, from 0: a thousand steps for a number, one per value for an integer. */
	static int positions(FireParameter parameter) {
		if (parameter.kind() == FireParameter.Kind.INTEGER) {
			return (int) Math.round(parameter.max() - parameter.min());
		}
		return NUMBER_STEPS;
	}

	/** The position of a value on its scale, 0 at the minimum. */
	static int toPosition(FireParameter parameter, double value) {
		double within = Math.max(parameter.min(), Math.min(parameter.max(), value));
		if (parameter.kind() == FireParameter.Kind.INTEGER) {
			return (int) Math.round(within - parameter.min());
		}
		return (int) Math.round((within - parameter.min()) / (parameter.max() - parameter.min()) * NUMBER_STEPS);
	}

	/** The value of a position of its scale. */
	static double fromPosition(FireParameter parameter, int position) {
		if (parameter.kind() == FireParameter.Kind.INTEGER) {
			return parameter.min() + position;
		}
		return parameter.min() + position * (parameter.max() - parameter.min()) / NUMBER_STEPS;
	}

	/** A value as the tab shows it: "85 %", "96 columns", "1.7 rad/s", "#FFF2B0". */
	static String shown(Messages messages, FireParameter parameter, double value) {
		if (parameter.kind() == FireParameter.Kind.COLOUR) {
			return String.format(Locale.ROOT, "#%06X", ((long) value) & 0xFFFFFFL);
		}
		String number;
		if (parameter.kind() == FireParameter.Kind.INTEGER) {
			number = Long.toString(Math.round(value));
		} else {
			double scaled = parameter.unit() == FireParameter.Unit.SHARE ? value * 100.0 : value;
			double hundredths = Math.round(scaled * 100.0) / 100.0;
			// "2" rather than "2.0"
			number = hundredths == Math.rint(hundredths) ? Long.toString(Math.round(hundredths)) : String.valueOf(hundredths);
		}
		String unit = parameter.unit().labelKey();
		return unit == null ? number : number + " " + messages.get(unit);
	}

	/** The tooltip of a setting: what it changes, its unit and its default. */
	static String tooltip(Messages messages, FireParameter parameter, double byDefault) {
		StringBuilder text = new StringBuilder(messages.get(parameter.tooltipKey()));
		String unit = parameter.unit().labelKey();
		if (unit != null) {
			text.append('\n').append(messages.get("practice.fire.tooltip.unit", new String[] { messages.get(unit) }));
		}
		text.append('\n').append(messages.get("practice.fire.tooltip.default", new String[] { shown(messages, parameter, byDefault) }));
		return text.toString();
	}
}
