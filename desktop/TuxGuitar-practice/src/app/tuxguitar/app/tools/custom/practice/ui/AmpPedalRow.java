package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import app.tuxguitar.audio.engine.monitor.EffectKind;
import app.tuxguitar.audio.engine.monitor.Knob;
import app.tuxguitar.audio.engine.monitor.PedalSettings;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UIToggleButton;

/**
 * One pedal of the Amp and effects window (spec 022, contracts/amp-window.md "Layout" 2, FR-005, FR-016): its switch with its name, up, down and remove,
 * then one slider per knob with its value. Every control has a tooltip. A slider moves by the knob's step (half a step on a 0–10 scale, 1 dB, 1 %, 0.1 Hz,
 * 1 or 10 ms), so that its ends are the knob's minimum and maximum.
 */
final class AmpPedalRow {

	/** What the row asks of the chain. */
	interface Listener {
		void switched(EffectKind kind, boolean enabled);

		void knob(EffectKind kind, String key, double value);

		void up(EffectKind kind);

		void down(EffectKind kind);

		void remove(EffectKind kind);
	}

	private static final float SCALE_WIDTH = 120f;

	private final EffectKind kind;
	private final UIPanel panel;
	private final UIToggleButton enabled;
	private final UIButton up;
	private final UIButton down;
	private final UIButton remove;
	private final Map<String, UIScale> scales = new LinkedHashMap<String, UIScale>();
	private final Map<String, UILabel> values = new LinkedHashMap<String, UILabel>();
	private boolean updating;

	AmpPedalRow(UIFactory factory, UIContainer parent, UITableLayout parentLayout, int column, AmpWindow.Texts texts, PedalSettings pedal, boolean first,
			boolean last, final Listener listener) {
		this.kind = pedal.kind();
		this.panel = factory.createPanel(parent, true);
		UITableLayout layout = new UITableLayout();
		this.panel.setLayout(layout);
		parentLayout.set(this.panel, 1, column, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_TOP, false, false);

		String name = AmpTexts.pedal(this.kind);
		this.enabled = factory.createToggleButton(this.panel);
		this.enabled.setText(texts.get(name));
		this.enabled.setToolTipText(texts.get(name + ".tip"));
		this.enabled.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if (!AmpPedalRow.this.updating) {
					listener.switched(AmpPedalRow.this.kind, AmpPedalRow.this.enabled.isSelected());
				}
			}
		});
		layout.set(this.enabled, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);

		UIPanel buttons = factory.createPanel(this.panel, false);
		UITableLayout buttonsLayout = new UITableLayout(0f);
		buttons.setLayout(buttonsLayout);
		layout.set(buttons, 1, 2, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		this.up = button(factory, buttons, buttonsLayout, 1, texts, AmpTexts.PEDAL_UP, () -> listener.up(this.kind));
		this.up.setEnabled(!first);
		this.down = button(factory, buttons, buttonsLayout, 2, texts, AmpTexts.PEDAL_DOWN, () -> listener.down(this.kind));
		this.down.setEnabled(!last);
		this.remove = button(factory, buttons, buttonsLayout, 3, texts, AmpTexts.PEDAL_REMOVE, () -> listener.remove(this.kind));

		int line = 2;
		for (final Knob knob : this.kind.knobs()) {
			String key = AmpTexts.knob(this.kind, knob);
			String tip = texts.get(key + ".tip");
			UILabel label = factory.createLabel(this.panel);
			label.setText(texts.get(key));
			label.setToolTipText(tip);
			layout.set(label, line, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
			final UIScale scale = factory.createHorizontalScale(this.panel);
			scale.setMinimum(0);
			scale.setMaximum(positions(knob));
			scale.setIncrement(1);
			scale.setToolTipText(tip);
			scale.addSelectionListener(new UISelectionListener() {
				public void onSelect(UISelectionEvent event) {
					if (!AmpPedalRow.this.updating) {
						double value = knob.clamp(knob.min() + scale.getValue() * step(knob));
						AmpPedalRow.this.values.get(knob.key()).setText(format(knob, value));
						listener.knob(AmpPedalRow.this.kind, knob.key(), value);
					}
				}
			});
			layout.set(scale, line, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
			layout.set(scale, UITableLayout.PACKED_WIDTH, SCALE_WIDTH);
			UILabel value = factory.createLabel(this.panel);
			value.setToolTipText(tip);
			layout.set(value, line, 3, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
			layout.set(value, UITableLayout.PACKED_WIDTH, 40f);
			this.scales.put(knob.key(), scale);
			this.values.put(knob.key(), value);
			line++;
		}
		show(pedal);
	}

	private static UIButton button(UIFactory factory, UIPanel parent, UITableLayout layout, int column, AmpWindow.Texts texts, String key,
			final Runnable action) {
		UIButton button = factory.createButton(parent);
		button.setText(texts.get(key));
		button.setToolTipText(texts.get(key + ".tip"));
		button.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				action.run();
			}
		});
		layout.set(button, 1, column, UITableLayout.ALIGN_CENTER, UITableLayout.ALIGN_CENTER, false, false);
		return button;
	}

	/** A slider's step for that knob. */
	static double step(Knob knob) {
		String unit = knob.unit();
		if (unit.isEmpty()) {
			return 0.5;
		}
		if (unit.equals("Hz")) {
			return 0.1;
		}
		if (unit.equals("ms")) {
			return knob.max() - knob.min() > 200 ? 10 : 1;
		}
		return 1;
	}

	static int positions(Knob knob) {
		return (int) Math.round((knob.max() - knob.min()) / step(knob));
	}

	static String format(Knob knob, double value) {
		if (knob.unit().equals("Hz")) {
			return String.format(java.util.Locale.ROOT, "%.1f", value);
		}
		double rounded = Math.round(value * 2) / 2.0;
		return rounded == Math.rint(rounded) ? Long.toString(Math.round(rounded)) : String.valueOf(rounded);
	}

	/** Shows the pedal's settings without handing them on. */
	void show(PedalSettings pedal) {
		this.updating = true;
		try {
			this.enabled.setSelected(pedal.enabled());
			for (Knob knob : this.kind.knobs()) {
				double value = pedal.value(knob.key());
				this.scales.get(knob.key()).setValue((int) Math.round((value - knob.min()) / step(knob)));
				this.values.get(knob.key()).setText(format(knob, value));
			}
		} finally {
			this.updating = false;
		}
	}

	void dispose() {
		this.panel.dispose();
	}

	EffectKind getKind() {
		return this.kind;
	}

	UIToggleButton getSwitch() {
		return this.enabled;
	}

	UIButton getUp() {
		return this.up;
	}

	UIButton getDown() {
		return this.down;
	}

	UIButton getRemove() {
		return this.remove;
	}

	UIScale getScale(String key) {
		return this.scales.get(key);
	}
}
