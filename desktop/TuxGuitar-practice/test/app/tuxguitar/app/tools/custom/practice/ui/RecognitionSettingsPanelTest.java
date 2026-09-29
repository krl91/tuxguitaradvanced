package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.PassagePolicy;
import app.tuxguitar.app.tools.custom.practice.session.PassageSettings;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;
import app.tuxguitar.app.tools.custom.tunernext.TunerDisplay;
import app.tuxguitar.app.tools.custom.tunernext.TunerHold;
import app.tuxguitar.app.tools.custom.tunernext.TunerRecognition;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UICheckBox;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.util.TGContext;

/**
 * The real "Audio recognition" page ({@link RecognitionSettingsPanel}, the widgets of
 * {@link AudioRecognitionOption}) on a headless toolkit that follows SWT's native {@code Scale}
 * contract (P13 regression, T102). Covers: independent sliders with immediate value texts, exact
 * round trips of every numeric setting, each setting shown exactly once, OK → saved → relaunch,
 * propagation to the engine, tuner, practice and passages, Cancel, the three restore scopes, and
 * complete French texts. It does not replace the native SWT test (profile swt-gui-tests).
 */
public class RecognitionSettingsPanelTest {

	private static final InputAssessment SUITABLE = new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);

	/** The page as opened in the Configuration window. */
	private static final class Page {
		final SwtContractToolkit toolkit = new SwtContractToolkit();
		final RecognitionSettingsPanel panel;
		final UIPanel root;

		Page(RecognitionSettings initial, RecognitionSettingsPanel.Messages messages) {
			this.root = (UIPanel) this.toolkit.create(UIPanel.class, null);
			this.root.setLayout(new UITableLayout());
			this.panel = new RecognitionSettingsPanel(this.toolkit.factory(), this.root, messages, initial, null, null);
			this.panel.build();
		}

		UIScale scale(RecognitionParameter.Number parameter) {
			return (UIScale) this.panel.getControl(parameter);
		}

		String text(RecognitionParameter parameter) {
			return this.panel.getValueLabel(parameter).getText();
		}

		/** Every control's state and value text, to prove that nothing else moved. */
		Map<RecognitionParameter, String> snapshot() {
			Map<RecognitionParameter, String> result = new HashMap<RecognitionParameter, String>();
			for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
				UIControl control = this.panel.getControl(parameter);
				String state;
				if( control instanceof UIScale ) {
					state = Integer.toString(((UIScale) control).getValue());
				} else if( control instanceof UICheckBox ) {
					state = Boolean.toString(((UICheckBox) control).isSelected());
				} else {
					state = String.valueOf(((UIDropDownSelect<?>) control).getSelectedValue());
				}
				result.put(parameter, state + " | " + text(parameter));
			}
			return result;
		}
	}

	// ---------------------------------------------------------------- P13: independent sliders

	@Test
	public void minimumLevelAndHighLevelSlidersMoveIndependentlyAndShowTheirValueAtOnce() throws IOException {
		Page page = new Page(RecognitionSettings.defaults(), french());
		UIScale min = page.scale(RecognitionParameters.INPUT_LEVEL_MIN);
		UIScale max = page.scale(RecognitionParameters.INPUT_LEVEL_MAX);
		// opening shows the recommended values at their real positions, not at the far left
		assertEquals("-40 dBFS (recommandé)", page.text(RecognitionParameters.INPUT_LEVEL_MIN));
		assertEquals("-6 dBFS (recommandé)", page.text(RecognitionParameters.INPUT_LEVEL_MAX));
		assertEquals(20, min.getValue());
		assertEquals(14, max.getValue());
		assertEquals(35, min.getMaximum());
		assertEquals(19, max.getMaximum());

		// first slider: a normal value
		SwtContractToolkit.userMoves(min, 30);
		assertEquals("-30 dBFS (recommandé : -40 dBFS)", page.text(RecognitionParameters.INPUT_LEVEL_MIN));
		assertEquals(30, min.getValue());
		assertEquals(14, max.getValue());
		assertEquals("-6 dBFS (recommandé)", page.text(RecognitionParameters.INPUT_LEVEL_MAX));

		// second slider: its exact minimum; the first keeps position and text
		SwtContractToolkit.userMoves(max, 0);
		assertEquals("-20 dBFS (recommandé : -6 dBFS)", page.text(RecognitionParameters.INPUT_LEVEL_MAX));
		assertEquals(30, min.getValue());
		assertEquals("-30 dBFS (recommandé : -40 dBFS)", page.text(RecognitionParameters.INPUT_LEVEL_MIN));

		// reverse order, exact maximum then exact minimum
		SwtContractToolkit.userMoves(max, 19);
		assertEquals("-1 dBFS (recommandé : -6 dBFS)", page.text(RecognitionParameters.INPUT_LEVEL_MAX));
		SwtContractToolkit.userMoves(min, 0);
		assertEquals("-60 dBFS (recommandé : -40 dBFS)", page.text(RecognitionParameters.INPUT_LEVEL_MIN));
		assertEquals(19, max.getValue());
		assertEquals("-1 dBFS (recommandé : -6 dBFS)", page.text(RecognitionParameters.INPUT_LEVEL_MAX));
		SwtContractToolkit.userMoves(min, 35);
		assertEquals("-25 dBFS (recommandé : -40 dBFS)", page.text(RecognitionParameters.INPUT_LEVEL_MIN));
		assertEquals(19, max.getValue());

		assertEquals(-25.0, page.panel.getPending().get(RecognitionParameters.INPUT_LEVEL_MIN));
		assertEquals(-1.0, page.panel.getPending().get(RecognitionParameters.INPUT_LEVEL_MAX));
	}

	@Test
	public void everySliderChangesOnlyItsOwnValueAndTextAtItsMinimumMiddleAndMaximum() throws IOException {
		Page page = new Page(RecognitionSettings.defaults(), english());
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			if( !(parameter instanceof RecognitionParameter.Number) ) {
				continue;
			}
			RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			UIScale scale = page.scale(number);
			int top = RecognitionControls.sliderMaximum(number);
			assertEquals(0, scale.getMinimum(), number.getKey());
			assertEquals(top, scale.getMaximum(), number.getKey() + ": the native range must be the real one");
			for( int position : new int[] { top / 2, 0, top } ) {
				Map<RecognitionParameter, String> before = page.snapshot();
				SwtContractToolkit.userMoves(scale, position);
				Map<RecognitionParameter, String> after = page.snapshot();

				// its own value: stored, shown at once, in the right unit
				double expected = number.getMinimum() + position / (double) (steps(number));
				assertEquals(expected, page.panel.getPending().get(number), 1e-9, number.getKey() + " @" + position);
				assertEquals(position, scale.getValue(), number.getKey());
				String shown = page.text(number);
				assertTrue(shown.startsWith(RecognitionControls.displayNumber(number, expected) + unitSuffix(number)),
						number.getKey() + " @" + position + ": " + shown);
				assertEquals(expected, parseShown(number, shown), 1e-9, number.getKey() + ": shown number");
				// nothing else moved
				for( RecognitionParameter other : RecognitionParameters.ALL ) {
					if( other != number ) {
						assertEquals(before.get(other), after.get(other), number.getKey() + " moved " + other.getKey());
					}
				}
			}
			// back to the recommended value for the next setting
			SwtContractToolkit.userMoves(scale, RecognitionControls.toSlider(number, number.getDefault()));
		}
	}

	@Test
	public void theOnlyControlAnotherSliderMayMoveIsTheAcceptanceRangeRaisedToTheInTuneRange() throws IOException {
		Page page = new Page(RecognitionSettings.defaults(), english());
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.NOTE_ACCEPT), 0);
		assertEquals("10 cents (recommended: 35 cents)", page.text(RecognitionParameters.NOTE_ACCEPT));
		Map<RecognitionParameter, String> before = page.snapshot();

		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.TUNER_IN_TUNE), RecognitionControls.sliderMaximum(RecognitionParameters.TUNER_IN_TUNE));

		assertEquals("20 cents (recommended: 5 cents)", page.text(RecognitionParameters.TUNER_IN_TUNE));
		assertEquals("20 cents (recommended: 35 cents)", page.text(RecognitionParameters.NOTE_ACCEPT));
		assertEquals(10, page.scale(RecognitionParameters.NOTE_ACCEPT).getValue());
		Map<RecognitionParameter, String> after = page.snapshot();
		for( RecognitionParameter other : RecognitionParameters.ALL ) {
			if( other != RecognitionParameters.TUNER_IN_TUNE && other != RecognitionParameters.NOTE_ACCEPT ) {
				assertEquals(before.get(other), after.get(other), other.getKey());
			}
		}
	}

	@Test
	public void checkBoxesAndListsChangeOnlyThemselves() throws IOException {
		Page page = new Page(RecognitionSettings.defaults(), english());
		UICheckBox hold = (UICheckBox) page.panel.getControl(RecognitionParameters.TUNER_HOLD);
		@SuppressWarnings("unchecked")
		UIDropDownSelect<String> measures = (UIDropDownSelect<String>) page.panel.getControl(RecognitionParameters.PASSAGE_MEASURES);
		Map<RecognitionParameter, String> before = page.snapshot();

		hold.setSelected(false);
		SwtContractToolkit.userSelects(hold);
		measures.setSelectedValue("0");
		SwtContractToolkit.userSelects(measures);

		assertEquals("Off (recommended: On)", page.text(RecognitionParameters.TUNER_HOLD));
		assertEquals("(recommended: Every 2 measures)", page.text(RecognitionParameters.PASSAGE_MEASURES));
		assertFalse(page.panel.getPending().get(RecognitionParameters.TUNER_HOLD));
		assertEquals(0, page.panel.getPending().getPassageMeasures());
		Map<RecognitionParameter, String> after = page.snapshot();
		for( RecognitionParameter other : RecognitionParameters.ALL ) {
			if( other != RecognitionParameters.TUNER_HOLD && other != RecognitionParameters.PASSAGE_MEASURES ) {
				assertEquals(before.get(other), after.get(other), other.getKey());
			}
		}
	}

	// ---------------------------------------------------------------- each setting once, in its category

	@Test
	public void everySettingAppearsExactlyOnceInItsCategory() throws IOException {
		Page page = new Page(RecognitionSettings.defaults(), english());
		assertEquals(Section.values().length, SwtContractToolkit.state(page.panel.getCategorySelect()).items.size());
		Set<RecognitionParameter> seen = new HashSet<RecognitionParameter>();
		for( Section section : Section.values() ) {
			List<RecognitionParameter> shown = page.panel.getShown(section);
			assertEquals(RecognitionParameters.of(section), shown, section.name());
			List<Object> widgets = SwtContractToolkit.descendants(page.panel.getSectionPanel(section));
			for( RecognitionParameter parameter : shown ) {
				assertTrue(seen.add(parameter), parameter.getKey() + " shown twice");
				assertTrue(widgets.contains(page.panel.getControl(parameter)), parameter.getKey() + " not in " + section);
				assertTrue(widgets.contains(page.panel.getNameLabel(parameter)), parameter.getKey());
				assertTrue(widgets.contains(page.panel.getValueLabel(parameter)), parameter.getKey());
				assertEquals(section, parameter.getSection());
			}
			// one input control per setting in the category, no stray or hidden duplicate
			int inputs = 0;
			for( Object widget : widgets ) {
				if( widget instanceof UIScale || widget instanceof UICheckBox || widget instanceof UIDropDownSelect ) {
					inputs ++;
				}
			}
			assertEquals(shown.size(), inputs, section.name());
		}
		assertEquals(new HashSet<RecognitionParameter>(RecognitionParameters.ALL), seen);
	}

	@Test
	public void choosingACategoryShowsOnlyThatCategory() throws IOException {
		Page page = new Page(RecognitionSettings.defaults(), english());
		for( Section selected : Section.values() ) {
			page.panel.getCategorySelect().setSelectedValue(selected);
			SwtContractToolkit.userSelects(page.panel.getCategorySelect());
			for( Section section : Section.values() ) {
				assertEquals(section == selected, page.panel.getSectionPanel(section).isVisible(), selected + "/" + section);
			}
		}
	}

	// ---------------------------------------------------------------- OK, relaunch, propagation

	@Test
	public void okSavesWhatIsShownAndARelaunchShowsTheSame() throws IOException {
		MemoryConfiguration files = new MemoryConfiguration();
		RecognitionSettingsStore store = RecognitionSettingsStore.getInstance(files.newRun());
		Page page = new Page(store.get(), english());
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.INPUT_LEVEL_MIN), 30);
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.NOTE_CONFIDENCE), 40);
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.NOTE_ATTACK), 0);
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.CHORD_WINDOW), RecognitionControls.sliderMaximum(RecognitionParameters.CHORD_WINDOW));
		Map<RecognitionParameter, String> shown = page.snapshot();

		AudioRecognitionOption.commit(store, page.panel.getPending());

		// the file holds exactly what was shown
		Map<String, String> file = files.files.get(RecognitionSettingsStore.MODULE);
		assertEquals("-30", file.get(RecognitionParameters.INPUT_LEVEL_MIN.getKey()));
		assertEquals("0.9", file.get(RecognitionParameters.NOTE_CONFIDENCE.getKey()));
		assertEquals("2", file.get(RecognitionParameters.NOTE_ATTACK.getKey()));
		assertEquals("1500", file.get(RecognitionParameters.CHORD_WINDOW.getKey()));

		// reopening in the same run, and after a relaunch, shows the same page
		assertEquals(shown, new Page(store.get(), english()).snapshot());
		RecognitionSettingsStore relaunched = RecognitionSettingsStore.getInstance(files.newRun());
		assertTrue(relaunched.getLoaded().getRepairs().isEmpty());
		assertEquals(shown, new Page(relaunched.get(), english()).snapshot());
	}

	@Test
	public void theValuesShownReachTheEngineTheTunerPracticeAndPassages() throws IOException {
		MemoryConfiguration files = new MemoryConfiguration();
		TGContext run = files.newRun();
		RecognitionSettingsStore store = RecognitionSettingsStore.getInstance(run);
		AudioEngine engine = AudioEngine.getInstance(run);
		assertEquals(-40.0, engine.getLevelAnalyzer().getTooLowRmsDbfs());
		assertEquals(4.5, engine.getArticulationDetector().getGateDb());

		Page page = new Page(store.get(), english());
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.INPUT_LEVEL_MIN), 30);
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.NOTE_ATTACK), 55);
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.TUNER_IN_TUNE), 9);
		UICheckBox hold = (UICheckBox) page.panel.getControl(RecognitionParameters.TUNER_HOLD);
		hold.setSelected(false);
		SwtContractToolkit.userSelects(hold);
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.NOTE_ACCEPT), 10);
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.NOTE_CONFIDENCE), 40);
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.NOTE_STABILITY), 250);
		@SuppressWarnings("unchecked")
		UIDropDownSelect<String> measures = (UIDropDownSelect<String>) page.panel.getControl(RecognitionParameters.PASSAGE_MEASURES);
		measures.setSelectedValue("4");
		SwtContractToolkit.userSelects(measures);
		@SuppressWarnings("unchecked")
		UIDropDownSelect<String> threshold = (UIDropDownSelect<String>) page.panel.getControl(RecognitionParameters.PASSAGE_THRESHOLD);
		threshold.setSelectedValue("75");
		SwtContractToolkit.userSelects(threshold);
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.PASSAGE_RETRIES), 5);
		assertEquals("-30 dBFS (recommended: -40 dBFS)", page.text(RecognitionParameters.INPUT_LEVEL_MIN));
		assertEquals("7.5 dB (recommended: 4.5 dB)", page.text(RecognitionParameters.NOTE_ATTACK));
		assertEquals("10 cents (recommended: 5 cents)", page.text(RecognitionParameters.TUNER_IN_TUNE));
		assertEquals("20 cents (recommended: 35 cents)", page.text(RecognitionParameters.NOTE_ACCEPT));
		assertEquals("90 % (recommended: 80 %)", page.text(RecognitionParameters.NOTE_CONFIDENCE));
		assertEquals("300 ms (recommended: 107 ms)", page.text(RecognitionParameters.NOTE_STABILITY));
		assertEquals("5 (recommended: 2)", page.text(RecognitionParameters.PASSAGE_RETRIES));

		AudioRecognitionOption.commit(store, page.panel.getPending());

		// engine: the analysers handed to the analysis
		assertEquals(-30.0, engine.getLevelAnalyzer().getTooLowRmsDbfs());
		assertEquals(7.5, engine.getArticulationDetector().getGateDb());
		// −37 dBFS: suitable for the recommended −40 dBFS floor, too low for the chosen −30
		float[] signal = sine(440.0, 48000, 2048, 0.02);
		assertEquals(AnalysisFrame.LevelState.TOO_LOW, engine.getLevelAnalyzer().analyze(signal).getLevelState());

		// tuner: what it shows
		TunerRecognition tuner = new TunerRecognition(store);
		assertEquals(0.80, tuner.interpreter().getConfidenceThreshold());
		TunerHold.Shown sharp = tuner.show(frame(hz(45, 8), 0.95), SUITABLE, null, 0);
		assertEquals(TunerDisplay.State.IN_TUNE, sharp.getDisplay().getState());
		assertFalse(tuner.show(frame(Double.NaN, 0.0), SUITABLE, null, 0).isHeld());

		// practice: the session built as the practice window builds it
		PracticeParameters practice = PracticeParameters.of(store.get());
		assertEquals(20.0, practice.tolerance().getPracticeAcceptCents());
		assertEquals(0.9, practice.noteInterpreter().getConfidenceThreshold());
		// 25 cents sharp: accepted with the recommended 35 cents, not with the chosen 20
		assertFalse(accepts(PracticeParameters.of(store.get()), 64, 25.0, 0.95, 400_000_000L));
		assertTrue(accepts(PracticeParameters.of(RecognitionSettings.defaults()), 64, 25.0, 0.95, 400_000_000L));
		// certainty 0.85: below the chosen 90 %
		assertFalse(accepts(practice, 64, 0.0, 0.85, 400_000_000L));
		// 300 ms to confirm: not yet after 200 ms, accepted after 400 ms
		assertFalse(accepts(practice, 64, 0.0, 0.95, 200_000_000L));
		assertTrue(accepts(practice, 64, 0.0, 0.95, 400_000_000L));

		// passages: the policy a new session gets
		PassagePolicy policy = PassageSettings.getInstance(run).toPolicy();
		assertEquals(4, policy.getMeasuresPerPassage());
		assertEquals(0.75, policy.getMaxErrorRate());
		assertEquals(5, policy.getMaxRetries());
	}

	// ---------------------------------------------------------------- Cancel and the three restore scopes

	@Test
	public void cancelPropagatesNothing() throws IOException {
		MemoryConfiguration files = new MemoryConfiguration();
		TGContext run = files.newRun();
		RecognitionSettingsStore store = RecognitionSettingsStore.getInstance(run);
		AudioEngine engine = AudioEngine.getInstance(run);
		Page page = new Page(store.get(), english());
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.INPUT_LEVEL_MIN), 0);
		SwtContractToolkit.userSelects(page.panel.getRestoreAllButton());
		SwtContractToolkit.userMoves(page.scale(RecognitionParameters.NOTE_ACCEPT), 0);

		// Cancel: the page is closed without commit
		page.panel.dispose();

		assertEquals(RecognitionSettings.defaults(), store.get());
		assertNull(files.files.get(RecognitionSettingsStore.MODULE));
		assertEquals(-40.0, engine.getLevelAnalyzer().getTooLowRmsDbfs());
		assertEquals("35 cents (recommended)", new Page(store.get(), english()).text(RecognitionParameters.NOTE_ACCEPT));
	}

	@Test
	public void restoringACategoryChangesOnlyThatCategoryAndOnlyWithOk() throws IOException {
		MemoryConfiguration files = new MemoryConfiguration();
		RecognitionSettingsStore store = RecognitionSettingsStore.getInstance(files.newRun());
		RecognitionSettings custom = RecognitionSettings.defaults().with(RecognitionParameters.NOTE_ACCEPT, 20)
				.with(RecognitionParameters.NOTE_CONFIDENCE, 0.9).with(RecognitionParameters.CHORD_WINDOW, 900)
				.with(RecognitionParameters.TUNER_IN_TUNE, 8);
		store.update(custom);
		Page page = new Page(store.get(), english());

		SwtContractToolkit.userSelects(page.panel.getRestoreSectionButton(Section.NOTES));

		assertEquals("35 cents (recommended)", page.text(RecognitionParameters.NOTE_ACCEPT));
		assertEquals("80 % (recommended)", page.text(RecognitionParameters.NOTE_CONFIDENCE));
		assertEquals("900 ms (recommended: 600 ms)", page.text(RecognitionParameters.CHORD_WINDOW));
		assertEquals("8 cents (recommended: 5 cents)", page.text(RecognitionParameters.TUNER_IN_TUNE));
		assertEquals(custom, store.get(), "nothing saved before OK");

		AudioRecognitionOption.commit(store, page.panel.getPending());
		assertEquals(35.0, store.get().get(RecognitionParameters.NOTE_ACCEPT));
		assertEquals(900.0, store.get().get(RecognitionParameters.CHORD_WINDOW));
		assertEquals(8.0, store.get().get(RecognitionParameters.TUNER_IN_TUNE));
	}

	@Test
	public void restoringTheWholePageChangesEveryCategoryAndOnlyWithOk() throws IOException {
		RecognitionSettingsStore store = RecognitionSettingsStore.getInstance(new MemoryConfiguration().newRun());
		RecognitionSettings custom = RecognitionSettings.defaults().with(RecognitionParameters.INPUT_LEVEL_MIN, -30)
				.with(RecognitionParameters.CHORD_WINDOW, 900).with(RecognitionParameters.PASSAGE_RETRIES, 4);
		store.update(custom);
		Page page = new Page(store.get(), english());

		SwtContractToolkit.userSelects(page.panel.getRestoreAllButton());

		assertEquals(new Page(RecognitionSettings.defaults(), english()).snapshot(), page.snapshot());
		assertEquals(custom, store.get(), "nothing saved before OK");
		AudioRecognitionOption.commit(store, page.panel.getPending());
		assertEquals(RecognitionSettings.defaults(), store.get());
	}

	@Test
	public void tuxGuitarsDefaultsButtonRestoresAudioRecognitionAtOnce() throws IOException {
		TGContext run = new MemoryConfiguration().newRun();
		RecognitionSettingsStore store = RecognitionSettingsStore.getInstance(run);
		AudioEngine engine = AudioEngine.getInstance(run);
		store.update(RecognitionSettings.defaults().with(RecognitionParameters.INPUT_LEVEL_MIN, -30));
		assertEquals(-30.0, engine.getLevelAnalyzer().getTooLowRmsDbfs());

		// the Settings dialog's own "Defaults": no OK needed, the whole of TuxGuitar is reset
		AudioRecognitionOption.applyTuxGuitarDefaults(store);

		assertEquals(RecognitionSettings.defaults(), store.get());
		assertEquals(-40.0, engine.getLevelAnalyzer().getTooLowRmsDbfs());
	}

	@Test
	public void theThreeRestoreActionsHaveDistinctExplicitLabels() throws IOException {
		Properties fr = load("share/lang/messages_fr.properties");
		assertEquals("Rétablir les valeurs recommandées de cette catégorie", fr.getProperty("recognition.restore-section"));
		assertEquals("Rétablir tous les réglages de reconnaissance audio", fr.getProperty("recognition.restore-all"));
		assertEquals("Catégorie de réglages :", fr.getProperty("recognition.category"));
		Properties core = load("../../common/resources/lang/messages_fr.properties");
		assertEquals("Valeurs par défaut", core.getProperty("defaults"));
		Set<String> labels = new HashSet<String>();
		labels.add(fr.getProperty("recognition.restore-section"));
		labels.add(fr.getProperty("recognition.restore-all"));
		labels.add(core.getProperty("defaults"));
		assertEquals(3, labels.size());
	}

	// ---------------------------------------------------------------- French

	@Test
	public void theWholePageExistsInFrenchWithoutFallingBackToEnglish() throws IOException {
		// the French messages fail on any missing key: building the page proves full coverage
		Page page = new Page(RecognitionSettings.defaults(), french());
		Properties en = load("share/lang/messages.properties");
		Properties fr = load("share/lang/messages_fr.properties");
		for( String key : en.stringPropertyNames() ) {
			if( key.startsWith("recognition.") ) {
				assertNotNull(fr.getProperty(key), "French text missing: " + key);
			}
		}
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			String name = page.panel.getNameLabel(parameter).getText();
			assertNotEquals(en.getProperty(RecognitionControls.nameKey(parameter)), name, "not translated: " + parameter.getKey());
			assertNotEquals(en.getProperty(RecognitionControls.tipKey(parameter)), fr.getProperty(RecognitionControls.tipKey(parameter)), parameter.getKey());
		}
		for( Section section : Section.values() ) {
			if( section == Section.PASSAGES ) {
				// the same word in both languages
				assertEquals("Passages", fr.getProperty(RecognitionControls.sectionKey(section)));
				continue;
			}
			assertNotEquals(en.getProperty(RecognitionControls.sectionKey(section)), fr.getProperty(RecognitionControls.sectionKey(section)), section.name());
		}
	}

	// ---------------------------------------------------------------- helpers

	private static double steps(RecognitionParameter.Number number) {
		return number.getDisplayFactor() * RecognitionControls.resolution(number);
	}

	private static String unitSuffix(RecognitionParameter.Number number) {
		switch( number.getUnit() ) {
			case "":
				return "";
			default:
				return " " + number.getUnit();
		}
	}

	/** The number at the start of a value text, back in stored units (independent of the panel's formatter). */
	private static double parseShown(RecognitionParameter.Number number, String shown) {
		String first = shown.split(" ")[0];
		return Double.parseDouble(first) / number.getDisplayFactor();
	}

	private static float[] sine(double frequencyHz, int sampleRate, int count, double amplitude) {
		float[] samples = new float[count];
		for( int i = 0 ; i < count ; i ++ ) {
			samples[i] = (float) (amplitude * Math.sin(2.0 * Math.PI * frequencyHz * i / sampleRate));
		}
		return samples;
	}

	private static AnalysisFrame frame(double f0Hz, double confidence) {
		return new AnalysisFrame(0L, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0Hz, confidence, false);
	}

	private static double hz(int midi, double cents) {
		return 440.0 * Math.pow(2.0, (midi - 69 + cents / 100.0) / 12.0);
	}

	/** Whether a practice session with these parameters accepts a fresh pluck held for {@code holdNanos}. */
	private static boolean accepts(PracticeParameters parameters, int midi, double cents, double confidence, long holdNanos) {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, midi - 64);
		fixture.quarter(0, 1, 1, midi - 64);
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(),
				PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeSession session = new PracticeSession(selection, fixture.sequence(), parameters.noteInterpreter(), parameters.tolerance(),
				parameters.stabilityNanos(), parameters.articulationLatencyNanos());
		session.setAssessment(SUITABLE);
		session.onCommand(PracticeCommand.START);
		long hop = PracticeParameters.HOP_NANOS;
		long now = 0;
		long latency = parameters.articulationLatencyNanos();
		double f0 = hz(midi, cents);
		for( long t = 0 ; t < latency ; t += hop ) {
			session.onFrame(new AnalysisFrame(now += hop, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0, confidence, false));
		}
		session.onFrame(new AnalysisFrame(now += hop, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0, confidence, true));
		for( long t = 0 ; t < holdNanos - latency ; t += hop ) {
			session.onFrame(new AnalysisFrame(now += hop, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0, confidence, false));
		}
		return session.getCurrentIndex() > 0;
	}

	private static RecognitionSettingsPanel.Messages english() throws IOException {
		return messages(load("share/lang/messages.properties"));
	}

	private static RecognitionSettingsPanel.Messages french() throws IOException {
		return messages(load("share/lang/messages_fr.properties"));
	}

	/** Texts from one file only; a missing key fails the test instead of falling back. */
	private static RecognitionSettingsPanel.Messages messages(final Properties properties) {
		return new RecognitionSettingsPanel.Messages() {
			public String get(String key) {
				String value = properties.getProperty(key);
				if( value == null ) {
					throw new AssertionError("missing text: " + key);
				}
				return value;
			}

			public String get(String key, String[] arguments) {
				String value = get(key);
				for( int i = 0 ; i < arguments.length ; i ++ ) {
					value = value.replace("{" + i + "}", arguments[i]);
				}
				return value;
			}
		};
	}

	private static Properties load(String path) throws IOException {
		Properties properties = new Properties();
		try (Reader reader = new InputStreamReader(Files.newInputStream(Paths.get(path)), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	@SuppressWarnings("unused")
	private static List<String> texts(Page page) {
		List<String> result = new ArrayList<String>();
		for( Object widget : SwtContractToolkit.descendants(page.root) ) {
			if( widget instanceof UILabel ) {
				result.add(((UILabel) widget).getText());
			}
		}
		return result;
	}
}
