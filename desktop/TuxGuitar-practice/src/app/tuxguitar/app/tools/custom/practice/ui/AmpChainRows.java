package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import app.tuxguitar.audio.engine.monitor.AmpSettings;
import app.tuxguitar.audio.engine.monitor.ChainSettings;
import app.tuxguitar.audio.engine.monitor.EffectKind;
import app.tuxguitar.audio.engine.monitor.PedalSettings;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UILegendPanel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.ui.widget.UIToggleButton;

/**
 * The chain's rows of the Amp and effects window (spec 022, contracts/amp-window.md "Layout" 2), {@link #ROWS} rows of the window from the first one given:
 * the pedals before the amp, the amp (its character and five sliders), the cabinet's switch, the pedals after the amp, and Add pedal…, which offers the
 * kinds not in the chain and puts each at the end of its own section. Each change builds the new chain and hands it on at once; adding, removing or moving
 * a pedal rebuilds the pedals' rows and lays the window out again.
 */
final class AmpChainRows implements AmpPedalRow.Listener {

	/** The rows of the window the chain takes. */
	static final int ROWS = 5;

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
	private final UIFactory factory;
	private final Runnable relayout;
	private final UIContainer before;
	private final UITableLayout beforeLayout;
	private final UIContainer after;
	private final UITableLayout afterLayout;
	/** Told the section of a pedal just added, so that its tab is shown (spec 025); null in the one-column window. */
	private final Consumer<EffectKind.Section> sectionShown;
	private final UIDropDownSelect<EffectKind> addPedal;
	private final List<EffectKind> offered = new ArrayList<EffectKind>();
	private final Map<EffectKind, AmpPedalRow> pedals = new EnumMap<EffectKind, AmpPedalRow>(EffectKind.class);
	private ChainSettings chain;
	private boolean updating;
	private boolean built;

	/** Where a part of the rows goes: a container, its layout and the row in it. */
	static final class Place {
		final UIContainer parent;
		final UITableLayout layout;
		final int row;

		Place(UIContainer parent, UITableLayout layout, int row) {
			this.parent = parent;
			this.layout = layout;
			this.row = row;
		}
	}

	/** @param relayout lays the window out again once the pedals' rows were rebuilt */
	AmpChainRows(UIFactory factory, UIContainer parent, UITableLayout parentLayout, int row, AmpWindow.Texts texts, ChainSettings chain,
			Consumer<ChainSettings> changed, Runnable relayout) {
		this(factory, texts, chain, changed, relayout, null, true, new Place(parent, parentLayout, row), new Place(parent, parentLayout, row + 1),
				new Place(parent, parentLayout, row + 2), new Place(parent, parentLayout, row + 3), new Place(parent, parentLayout, row + 4));
	}

	/**
	 * The rows in the tabs of the amp window (spec 025, contracts/amp-window.md): the pedals before the amp in {@code before}, the amp and its speaker
	 * simulation in {@code amp}, the pedals after it in {@code after}, each from the first row of its container, and Add a pedal at {@code add}.
	 *
	 * @param sectionShown told the section of a pedal just added, so that its tab is shown
	 */
	AmpChainRows(UIFactory factory, Place before, Place amp, Place after, Place add, AmpWindow.Texts texts, ChainSettings chain,
			Consumer<ChainSettings> changed, Runnable relayout, Consumer<EffectKind.Section> sectionShown) {
		this(factory, texts, chain, changed, relayout, sectionShown, false, before, amp, new Place(amp.parent, amp.layout, KNOBS.size() + 2), after, add);
	}

	/** @param legends the one-column window: each section in its titled frame at its place; else each section fills its container from its first row */
	private AmpChainRows(UIFactory factory, AmpWindow.Texts texts, ChainSettings chain, Consumer<ChainSettings> changed, Runnable relayout,
			Consumer<EffectKind.Section> sectionShown, boolean legends, Place beforePlace, Place ampPlace, Place cabinetPlace, Place afterPlace,
			Place addPlace) {
		this.texts = texts;
		this.changed = changed;
		this.chain = chain;
		this.factory = factory;
		this.relayout = relayout;
		this.sectionShown = sectionShown;

		if (legends) {
			UILegendPanel legend = factory.createLegendPanel(beforePlace.parent);
			legend.setText(texts.get(AmpTexts.BEFORE));
			this.before = legend;
			this.beforeLayout = new UITableLayout();
			legend.setLayout(this.beforeLayout);
			beforePlace.layout.set(legend, beforePlace.row, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_TOP, true, false);
		} else {
			this.before = beforePlace.parent;
			this.beforeLayout = beforePlace.layout;
		}

		UIContainer amp;
		UITableLayout layout;
		if (legends) {
			UILegendPanel legend = factory.createLegendPanel(ampPlace.parent);
			legend.setText(texts.get(AmpTexts.AMP));
			layout = new UITableLayout();
			legend.setLayout(layout);
			ampPlace.layout.set(legend, ampPlace.row, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_TOP, true, false);
			amp = legend;
		} else {
			amp = ampPlace.parent;
			layout = ampPlace.layout;
		}

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

		this.cabinet = factory.createToggleButton(cabinetPlace.parent);
		this.cabinet.setText(texts.get(AmpTexts.CABINET));
		this.cabinet.setToolTipText(texts.get(AmpTexts.CABINET + ".tip"));
		this.cabinet.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if (!AmpChainRows.this.updating) {
					change(AmpChainRows.this.chain.amp().withCabinet(AmpChainRows.this.cabinet.isSelected()));
				}
			}
		});
		cabinetPlace.layout.set(this.cabinet, cabinetPlace.row, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false, 1, legends ? 1 : 3);

		if (legends) {
			UILegendPanel legend = factory.createLegendPanel(afterPlace.parent);
			legend.setText(texts.get(AmpTexts.AFTER));
			this.after = legend;
			this.afterLayout = new UITableLayout();
			legend.setLayout(this.afterLayout);
			afterPlace.layout.set(legend, afterPlace.row, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_TOP, true, false);
		} else {
			this.after = afterPlace.parent;
			this.afterLayout = afterPlace.layout;
		}

		UIPanel add = factory.createPanel(addPlace.parent, false);
		UITableLayout addLayout = new UITableLayout(0f);
		add.setLayout(addLayout);
		addPlace.layout.set(add, addPlace.row, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		UILabel addLabel = factory.createLabel(add);
		addLabel.setText(texts.get(AmpTexts.ADD_PEDAL));
		addLabel.setToolTipText(texts.get(AmpTexts.ADD_PEDAL + ".tip"));
		addLayout.set(addLabel, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.addPedal = factory.createDropDownSelect(add);
		this.addPedal.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				EffectKind kind = AmpChainRows.this.addPedal.getSelectedValue();
				if (!AmpChainRows.this.updating && kind != null) {
					add(kind);
				}
			}
		});
		addLayout.set(this.addPedal, 1, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		show(chain);
		this.built = true;
	}

	private static List<PedalSettings> section(ChainSettings chain, EffectKind.Section section) {
		return section == EffectKind.Section.BEFORE ? chain.before() : chain.after();
	}

	private static ChainSettings withSection(ChainSettings chain, EffectKind.Section section, List<PedalSettings> pedals) {
		return section == EffectKind.Section.BEFORE ? chain.withBefore(pedals) : chain.withAfter(pedals);
	}

	private static int indexOf(List<PedalSettings> pedals, EffectKind kind) {
		for (int i = 0; i < pedals.size(); i++) {
			if (pedals.get(i).kind() == kind) {
				return i;
			}
		}
		return -1;
	}

	private void add(EffectKind kind) {
		List<PedalSettings> pedals = new ArrayList<PedalSettings>(section(this.chain, kind.section()));
		pedals.add(PedalSettings.defaults(kind));
		rebuild(withSection(this.chain, kind.section(), pedals));
		if (this.sectionShown != null) {
			this.sectionShown.accept(kind.section());
		}
	}

	private void move(EffectKind kind, int by) {
		List<PedalSettings> pedals = new ArrayList<PedalSettings>(section(this.chain, kind.section()));
		int from = indexOf(pedals, kind);
		int to = from + by;
		if (from < 0 || to < 0 || to >= pedals.size()) {
			return;
		}
		Collections.swap(pedals, from, to);
		rebuild(withSection(this.chain, kind.section(), pedals));
	}

	public void up(EffectKind kind) {
		move(kind, -1);
	}

	public void down(EffectKind kind) {
		move(kind, 1);
	}

	public void remove(EffectKind kind) {
		List<PedalSettings> pedals = new ArrayList<PedalSettings>(section(this.chain, kind.section()));
		pedals.remove(indexOf(pedals, kind));
		rebuild(withSection(this.chain, kind.section(), pedals));
	}

	public void switched(EffectKind kind, boolean enabled) {
		changePedal(this.chain.pedal(kind).withEnabled(enabled));
	}

	public void knob(EffectKind kind, String key, double value) {
		changePedal(this.chain.pedal(kind).with(key, value));
	}

	private void changePedal(PedalSettings pedal) {
		List<PedalSettings> pedals = new ArrayList<PedalSettings>(section(this.chain, pedal.kind().section()));
		pedals.set(indexOf(pedals, pedal.kind()), pedal);
		this.chain = withSection(this.chain, pedal.kind().section(), pedals);
		this.changed.accept(this.chain);
	}

	/** A pedal added, removed or moved: the rows built again, the chain handed on. */
	private void rebuild(ChainSettings value) {
		this.chain = value;
		this.updating = true;
		try {
			showPedals();
		} finally {
			this.updating = false;
		}
		this.changed.accept(this.chain);
		this.relayout.run();
	}

	/** The pedals' rows and the kinds offered by Add pedal…, from the chain. */
	private void showPedals() {
		for (AmpPedalRow row : this.pedals.values()) {
			row.dispose();
		}
		this.pedals.clear();
		row(this.before, this.beforeLayout, this.chain.before());
		row(this.after, this.afterLayout, this.chain.after());
		this.addPedal.removeItems();
		this.offered.clear();
		this.addPedal.addItem(new UISelectItem<EffectKind>("…", null));
		for (EffectKind kind : EffectKind.values()) {
			if (this.chain.pedal(kind) == null) {
				this.offered.add(kind);
				this.addPedal.addItem(new UISelectItem<EffectKind>(this.texts.get(AmpTexts.pedal(kind)), kind));
			}
		}
		this.addPedal.setSelectedValue(null);
		this.addPedal.setEnabled(!this.offered.isEmpty());
	}

	private void row(UIContainer panel, UITableLayout layout, List<PedalSettings> pedals) {
		for (int i = 0; i < pedals.size(); i++) {
			PedalSettings pedal = pedals.get(i);
			this.pedals.put(pedal.kind(), new AmpPedalRow(this.factory, panel, layout, i + 1, this.texts, pedal, i == 0, i == pedals.size() - 1, this));
		}
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
			showPedals();
		} finally {
			this.updating = false;
		}
		showValues();
		if (this.built) {
			this.relayout.run();
		}
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

	UIDropDownSelect<EffectKind> getAddPedal() {
		return this.addPedal;
	}

	/** The kinds Add pedal… offers, in its order (the drop-down has its prompt first). */
	List<EffectKind> getOffered() {
		return new ArrayList<EffectKind>(this.offered);
	}

	/** The row of that pedal, or null when it is not in the chain. */
	AmpPedalRow getPedal(EffectKind kind) {
		return this.pedals.get(kind);
	}
}
