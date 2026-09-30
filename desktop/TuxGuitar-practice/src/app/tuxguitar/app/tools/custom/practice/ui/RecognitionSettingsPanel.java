package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UICheckBox;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UISelectItem;

/**
 * The widgets of the "Audio recognition" Configuration page (task T102), built on the abstract UI
 * toolkit only, so the same code runs in TuxGuitar (SWT) and in tests. It edits a pending copy of
 * the settings: nothing is saved until the owner commits {@link #getPending()} (Configuration OK).
 *
 * <p>Each control shows exactly one setting. Changing a control updates that setting and its
 * displayed value at once; other controls are touched only when their own value changed (the
 * accept ≥ in-tune rule, or a restore), so moving one slider never moves another (P13).</p>
 */
final class RecognitionSettingsPanel {

	/** Translated texts ({@code TuxGuitar.getProperty} in the application). */
	interface Messages {
		String get(String key);

		String get(String key, String[] arguments);
	}

	/** Builds the shared input selector in the Input category; null when none (tests). */
	interface InputSection {
		UIControl create(UIFactory factory, UIPanel parent);

		void dispose();
	}

	/** Width of value texts: long enough for "−40 dBFS (recommandé : −40 dBFS)" without clipping. */
	static final float VALUE_WIDTH = 280f;

	private final UIFactory factory;
	private final UIPanel parent;
	private final Messages messages;
	private final InputSection input;
	private final Runnable packRequest;
	private final Map<Section, UIPanel> sections = new EnumMap<Section, UIPanel>(Section.class);
	private final Map<Section, List<RecognitionParameter>> shown = new EnumMap<Section, List<RecognitionParameter>>(Section.class);
	private final Map<Section, UIButton> restoreSectionButtons = new EnumMap<Section, UIButton>(Section.class);
	private final Map<RecognitionParameter, UIControl> controls = new HashMap<RecognitionParameter, UIControl>();
	private final Map<RecognitionParameter, UILabel> valueLabels = new HashMap<RecognitionParameter, UILabel>();
	private final Map<RecognitionParameter, UILabel> nameLabels = new HashMap<RecognitionParameter, UILabel>();
	/** The value each control currently shows; a control is updated only when this differs. */
	private final Map<RecognitionParameter, Object> displayed = new HashMap<RecognitionParameter, Object>();
	private RecognitionSettings pending;
	private UIDropDownSelect<Section> categorySelect;
	private UIButton restoreAllButton;
	private boolean updating;

	RecognitionSettingsPanel(UIFactory factory, UIPanel parent, Messages messages, RecognitionSettings initial,
			InputSection input, Runnable packRequest) {
		this.factory = factory;
		this.parent = parent;
		this.messages = messages;
		this.pending = initial;
		this.input = input;
		this.packRequest = packRequest;
	}

	/** Creates every widget; the Input category is shown first. */
	void build() {
		UITableLayout layout = (UITableLayout) this.parent.getLayout();

		UILabel categoryLabel = this.factory.createLabel(this.parent);
		categoryLabel.setText(this.messages.get("recognition.category"));
		layout.set(categoryLabel, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.categorySelect = this.factory.createDropDownSelect(this.parent);
		for( Section section : Section.values() ) {
			this.categorySelect.addItem(new UISelectItem<Section>(this.messages.get(RecognitionControls.sectionKey(section)), section));
		}
		layout.set(this.categorySelect, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.categorySelect.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				showSection(RecognitionSettingsPanel.this.categorySelect.getSelectedValue());
			}
		});

		for( Section section : Section.values() ) {
			UIPanel sectionPanel = this.factory.createPanel(this.parent, false);
			sectionPanel.setLayout(new UITableLayout());
			// every category shares one cell; only the selected one is visible
			layout.set(sectionPanel, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_TOP, true, false, 1, 2);
			buildSection(sectionPanel, section);
			this.sections.put(section, sectionPanel);
		}

		UILabel note = this.factory.createWrapLabel(this.parent);
		note.setText(this.messages.get("recognition.apply-note"));
		layout.set(note, 3, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		layout.set(note, UITableLayout.PACKED_WIDTH, RecognitionControls.TEXT_WIDTH);

		this.restoreAllButton = this.factory.createButton(this.parent);
		this.restoreAllButton.setText(this.messages.get("recognition.restore-all"));
		this.restoreAllButton.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				setPending(RecognitionSettings.defaults());
			}
		});
		layout.set(this.restoreAllButton, 4, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false, 1, 2);

		this.categorySelect.setSelectedValue(Section.INPUT);
		refresh();
		showSection(Section.INPUT);
	}

	private void buildSection(UIPanel panel, final Section section) {
		UITableLayout layout = (UITableLayout) panel.getLayout();
		int row = 1;
		if( section == Section.INPUT && this.input != null ) {
			UIControl selector = this.input.create(this.factory, panel);
			layout.set(selector, row ++, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 3);
			UILabel note = this.factory.createWrapLabel(panel);
			note.setText(this.messages.get("recognition.input.note"));
			layout.set(note, row ++, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 3);
			layout.set(note, UITableLayout.PACKED_WIDTH, RecognitionControls.TEXT_WIDTH);
		}
		List<RecognitionParameter> parameters = new ArrayList<RecognitionParameter>();
		for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
			addRow(panel, layout, row ++, parameter);
			parameters.add(parameter);
		}
		this.shown.put(section, Collections.unmodifiableList(parameters));
		UIButton restore = this.factory.createButton(panel);
		restore.setText(this.messages.get("recognition.restore-section"));
		restore.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				setPending(RecognitionSettingsPanel.this.pending.withDefaults(section));
			}
		});
		layout.set(restore, row, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false, 1, 3);
		this.restoreSectionButtons.put(section, restore);
	}

	private void addRow(UIPanel panel, UITableLayout layout, int row, final RecognitionParameter parameter) {
		String tip = tooltip(parameter);
		UILabel name = this.factory.createLabel(panel);
		name.setText(this.messages.get(RecognitionControls.nameKey(parameter)));
		name.setToolTipText(tip);
		layout.set(name, row, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.nameLabels.put(parameter, name);

		UILabel value = this.factory.createLabel(panel);
		value.setToolTipText(tip);
		layout.set(value, row, 3, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(value, UITableLayout.PACKED_WIDTH, VALUE_WIDTH);
		this.valueLabels.put(parameter, value);

		UIControl control;
		if( parameter instanceof RecognitionParameter.Number ) {
			final RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			final UIScale scale = this.factory.createHorizontalScale(panel);
			// maximum first, from a zero minimum: see RecognitionControls.sliderMaximum
			scale.setMaximum(RecognitionControls.sliderMaximum(number));
			scale.setMinimum(0);
			scale.setIncrement(1);
			scale.setToolTipText(tip);
			scale.addSelectionListener(new UISelectionListener() {
				public void onSelect(UISelectionEvent event) {
					if( !RecognitionSettingsPanel.this.updating ) {
						change(number, RecognitionControls.fromSlider(number, scale.getValue()));
					}
				}
			});
			layout.set(scale, row, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
			layout.set(scale, UITableLayout.PACKED_WIDTH, RecognitionControls.SLIDER_WIDTH);
			control = scale;
		} else if( parameter instanceof RecognitionParameter.Flag ) {
			final RecognitionParameter.Flag flag = (RecognitionParameter.Flag) parameter;
			final UICheckBox check = this.factory.createCheckBox(panel);
			check.setText(this.messages.get("recognition.on"));
			check.addSelectionListener(new UISelectionListener() {
				public void onSelect(UISelectionEvent event) {
					if( !RecognitionSettingsPanel.this.updating ) {
						change(flag, check.isSelected());
					}
				}
			});
			layout.set(check, row, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
			control = check;
		} else {
			final RecognitionParameter.Choice choice = (RecognitionParameter.Choice) parameter;
			final UIDropDownSelect<String> select = this.factory.createDropDownSelect(panel);
			for( String option : choice.getOptions() ) {
				select.addItem(new UISelectItem<String>(optionText(choice, option), option));
			}
			// a single real option is shown, not a simulated choice (e.g. one pitch engine)
			select.setEnabled(choice.getOptions().size() > 1);
			select.addSelectionListener(new UISelectionListener() {
				public void onSelect(UISelectionEvent event) {
					String selected = select.getSelectedValue();
					if( !RecognitionSettingsPanel.this.updating && selected != null ) {
						change(choice, selected);
					}
				}
			});
			layout.set(select, row, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
			control = select;
		}
		this.controls.put(parameter, control);
	}

	/** One control changed its own setting: update the pending settings and what changed. */
	private void change(RecognitionParameter parameter, Object value) {
		setPending(this.pending.with(parameter, value));
	}

	void setPending(RecognitionSettings settings) {
		this.pending = settings;
		refresh();
	}

	RecognitionSettings getPending() {
		return this.pending;
	}

	/** Brings each control and value text in line with the pending settings, touching only changed ones. */
	private void refresh() {
		this.updating = true;
		try {
			for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
				Object value = this.pending.getValue(parameter);
				if( value.equals(this.displayed.get(parameter)) ) {
					continue;
				}
				show(parameter, value);
				this.displayed.put(parameter, value);
			}
		} finally {
			this.updating = false;
		}
	}

	@SuppressWarnings("unchecked")
	private void show(RecognitionParameter parameter, Object value) {
		UIControl control = this.controls.get(parameter);
		String text;
		if( parameter instanceof RecognitionParameter.Number ) {
			RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			double current = (Double) value;
			UIScale scale = (UIScale) control;
			int position = RecognitionControls.toSlider(number, current);
			if( scale.getValue() != position ) {
				scale.setValue(position);
			}
			text = valueText(parameter, RecognitionControls.displayNumber(number, current) + unitText(number));
		} else if( parameter instanceof RecognitionParameter.Flag ) {
			boolean on = (Boolean) value;
			UICheckBox check = (UICheckBox) control;
			if( check.isSelected() != on ) {
				check.setSelected(on);
			}
			text = valueText(parameter, this.messages.get(on ? "recognition.on" : "recognition.off"));
		} else {
			RecognitionParameter.Choice choice = (RecognitionParameter.Choice) parameter;
			UIDropDownSelect<String> select = (UIDropDownSelect<String>) control;
			if( !value.equals(select.getSelectedValue()) ) {
				select.setSelectedValue((String) value);
			}
			text = (choice.getOptions().size() > 1 ? valueText(parameter, "") : this.messages.get("recognition.single-option"));
		}
		this.valueLabels.get(parameter).setText(text);
	}

	/** "35 cents (recommended)" or "20 cents (recommended: 35 cents)"; for lists only the mark. */
	String valueText(RecognitionParameter parameter, String shown) {
		boolean isDefault = this.pending.isDefault(parameter);
		if( shown.isEmpty() ) {
			return (isDefault ? this.messages.get("recognition.value.is-recommended")
					: this.messages.get("recognition.value.recommended-is", new String[] { recommendedText(parameter) }));
		}
		if( isDefault ) {
			return this.messages.get("recognition.value.recommended", new String[] { shown });
		}
		return this.messages.get("recognition.value.other", new String[] { shown, recommendedText(parameter) });
	}

	private String recommendedText(RecognitionParameter parameter) {
		if( parameter instanceof RecognitionParameter.Number ) {
			RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			return RecognitionControls.displayNumber(number, number.getDefault()) + unitText(number);
		}
		if( parameter instanceof RecognitionParameter.Flag ) {
			return this.messages.get(((RecognitionParameter.Flag) parameter).getDefault() ? "recognition.on" : "recognition.off");
		}
		RecognitionParameter.Choice choice = (RecognitionParameter.Choice) parameter;
		return optionName(choice, choice.getDefault());
	}

	private String optionName(RecognitionParameter.Choice choice, String option) {
		if( choice == RecognitionParameters.ENGINE_PITCH && PitchEngineCatalog.AUTOMATIC.equals(option) ) {
			return this.messages.get(RecognitionControls.optionKey(choice, option), new String[] { PitchEngineCatalog.getRecommended().getName() });
		}
		return this.messages.get(RecognitionControls.optionKey(choice, option));
	}

	private String optionText(RecognitionParameter.Choice choice, String option) {
		String text = optionName(choice, option);
		return (option.equals(choice.getDefault()) ? this.messages.get("recognition.option.recommended", new String[] { text }) : text);
	}

	private String unitText(RecognitionParameter.Number number) {
		String key = RecognitionControls.unitKey(number.getUnit());
		return (key != null ? " " + this.messages.get(key) : "");
	}

	/** Effect and trade-off (translated), then range and recommended value from the parameter itself. */
	String tooltip(RecognitionParameter parameter) {
		String tip = this.messages.get(RecognitionControls.tipKey(parameter));
		if( parameter instanceof RecognitionParameter.Number ) {
			RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			return tip + "\n" + this.messages.get("recognition.tip.range", new String[] {
					RecognitionControls.displayNumber(number, number.getMinimum()) + unitText(number),
					RecognitionControls.displayNumber(number, number.getMaximum()) + unitText(number),
					recommendedText(number) });
		}
		return tip + "\n" + this.messages.get("recognition.tip.recommended", new String[] { recommendedText(parameter) });
	}

	void showSection(Section selected) {
		for( Map.Entry<Section, UIPanel> entry : this.sections.entrySet() ) {
			entry.getValue().setVisible(entry.getKey() == selected);
		}
		if( this.packRequest != null ) {
			// grow to the category's packed size: no control outside the visible window
			this.packRequest.run();
		}
	}

	Section getSelectedSection() {
		return this.categorySelect.getSelectedValue();
	}

	void dispose() {
		if( this.input != null ) {
			this.input.dispose();
		}
	}

	// ---------------------------------------------------------------- for tests

	UIDropDownSelect<Section> getCategorySelect() {
		return this.categorySelect;
	}

	/** The settings shown in a category, in order. */
	List<RecognitionParameter> getShown(Section section) {
		return this.shown.get(section);
	}

	UIPanel getSectionPanel(Section section) {
		return this.sections.get(section);
	}

	UIControl getControl(RecognitionParameter parameter) {
		return this.controls.get(parameter);
	}

	UILabel getValueLabel(RecognitionParameter parameter) {
		return this.valueLabels.get(parameter);
	}

	UILabel getNameLabel(RecognitionParameter parameter) {
		return this.nameLabels.get(parameter);
	}

	UIButton getRestoreSectionButton(Section section) {
		return this.restoreSectionButtons.get(section);
	}

	UIButton getRestoreAllButton() {
		return this.restoreAllButton;
	}
}
