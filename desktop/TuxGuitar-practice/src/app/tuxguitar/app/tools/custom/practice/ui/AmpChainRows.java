package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import app.tuxguitar.audio.engine.monitor.AmpSettings;
import app.tuxguitar.audio.engine.monitor.ChainSettings;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UILegendPanel;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.ui.widget.UIToggleButton;

/**
 * The chain's rows of the Amp and effects window (spec 022, contracts/amp-window.md "Layout" 2): the amp (its character and five sliders) and the cabinet's
 * switch. The pedals' rows come with the pedals' user story. Each change builds the new chain and hands it on at once.
 */
final class AmpChainRows {

	/** The amp's sliders, in the order of the row. */
	static final List<String> KNOBS = Collections.unmodifiableList(Arrays.asList("gain", "bass", "middle", "treble", "volume"));

	/** Half steps from 0 to 10. */
	private static final int POSITIONS = 20;
	private static final float SCALE_WIDTH = 160f;

	private final AmpWindow.Texts texts;
	private final Consumer<ChainSettings> changed;
	private final UILabel characterLabel;
	private final UIDropDownSelect<AmpSettings.Character> character;
	private final Map<String, UIScale> scales = new LinkedHashMap<String, UIScale>();
	private final Map<String, UILabel> values = new LinkedHashMap<String, UILabel>();
	private final UIToggleButton cabinet;
	private ChainSettings chain;
	private boolean updating;

	AmpChainRows(UIFactory factory, UIContainer parent, UITableLayout parentLayout, int row, AmpWindow.Texts texts, ChainSettings chain,
			Consumer<ChainSettings> changed) {
		this.texts = texts;
		this.changed = changed;
		this.chain = chain;
		UILegendPanel amp = factory.createLegendPanel(parent);
		amp.setText(texts.get(AmpTexts.AMP));
		UITableLayout layout = new UITableLayout();
		amp.setLayout(layout);
		parentLayout.set(amp, row, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_TOP, true, false);

		this.characterLabel = factory.createLabel(amp);
		this.characterLabel.setText(texts.get(AmpTexts.CHARACTER));
		this.characterLabel.setToolTipText(texts.get(AmpTexts.CHARACTER + ".tip"));
		layout.set(this.characterLabel, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.character = factory.createDropDownSelect(amp);
		this.character.addItem(new UISelectItem<AmpSettings.Character>(texts.get(AmpTexts.CLEAN), AmpSettings.Character.CLEAN));
		this.character.addItem(new UISelectItem<AmpSettings.Character>(texts.get(AmpTexts.CRUNCH), AmpSettings.Character.CRUNCH));
		this.character.addItem(new UISelectItem<AmpSettings.Character>(texts.get(AmpTexts.HIGH_GAIN), AmpSettings.Character.HIGH_GAIN));
		this.character.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				AmpSettings.Character value = AmpChainRows.this.character.getSelectedValue();
				if (!AmpChainRows.this.updating && value != null) {
					change(AmpChainRows.this.chain.amp().withCharacter(value));
				}
			}
		});
		layout.set(this.character, 1, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false, 1, 2);

		int line = 2;
		for (final String knob : KNOBS) {
			String key = AmpTexts.P + knob;
			String tip = texts.get(key + ".tip");
			UILabel label = factory.createLabel(amp);
			label.setText(texts.get(key));
			label.setToolTipText(tip);
			layout.set(label, line, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
			final UIScale scale = factory.createHorizontalScale(amp);
			scale.setMinimum(0);
			scale.setMaximum(POSITIONS);
			scale.setIncrement(1);
			scale.setToolTipText(tip);
			scale.addSelectionListener(new UISelectionListener() {
				public void onSelect(UISelectionEvent event) {
					if (!AmpChainRows.this.updating) {
						change(with(AmpChainRows.this.chain.amp(), knob, scale.getValue() * 10.0 / POSITIONS));
					}
				}
			});
			layout.set(scale, line, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
			layout.set(scale, UITableLayout.PACKED_WIDTH, SCALE_WIDTH);
			UILabel value = factory.createLabel(amp);
			value.setToolTipText(tip);
			layout.set(value, line, 3, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
			layout.set(value, UITableLayout.PACKED_WIDTH, 32f);
			this.scales.put(knob, scale);
			this.values.put(knob, value);
			line++;
		}

		this.cabinet = factory.createToggleButton(parent);
		this.cabinet.setText(texts.get(AmpTexts.CABINET));
		this.cabinet.setToolTipText(texts.get(AmpTexts.CABINET + ".tip"));
		this.cabinet.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if (!AmpChainRows.this.updating) {
					change(AmpChainRows.this.chain.amp().withCabinet(AmpChainRows.this.cabinet.isSelected()));
				}
			}
		});
		parentLayout.set(this.cabinet, row + 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		show(chain);
	}

	private static AmpSettings with(AmpSettings amp, String knob, double value) {
		switch (knob) {
		case "gain":
			return amp.withGain(value);
		case "bass":
			return amp.withBass(value);
		case "middle":
			return amp.withMiddle(value);
		case "treble":
			return amp.withTreble(value);
		default:
			return amp.withVolume(value);
		}
	}

	private static double value(AmpSettings amp, String knob) {
		switch (knob) {
		case "gain":
			return amp.gain();
		case "bass":
			return amp.bass();
		case "middle":
			return amp.middle();
		case "treble":
			return amp.treble();
		default:
			return amp.volume();
		}
	}

	private void change(AmpSettings amp) {
		this.chain = this.chain.withAmp(amp);
		showValues();
		this.changed.accept(this.chain);
	}

	/** Shows a chain without handing it on (a sound picked, the window opened). */
	void show(ChainSettings value) {
		this.chain = value;
		this.updating = true;
		try {
			this.character.setSelectedValue(value.amp().character());
			for (String knob : KNOBS) {
				this.scales.get(knob).setValue((int) Math.round(value(value.amp(), knob) * POSITIONS / 10.0));
			}
			this.cabinet.setSelected(value.amp().cabinet());
		} finally {
			this.updating = false;
		}
		showValues();
	}

	private void showValues() {
		for (String knob : KNOBS) {
			double number = value(this.chain.amp(), knob);
			this.values.get(knob).setText(number == Math.rint(number) ? Long.toString(Math.round(number)) : String.valueOf(number));
		}
	}

	ChainSettings chain() {
		return this.chain;
	}

	UIDropDownSelect<AmpSettings.Character> getCharacter() {
		return this.character;
	}

	UILabel getCharacterLabel() {
		return this.characterLabel;
	}

	UIScale getScale(String knob) {
		return this.scales.get(knob);
	}

	UIToggleButton getCabinet() {
		return this.cabinet;
	}
}
