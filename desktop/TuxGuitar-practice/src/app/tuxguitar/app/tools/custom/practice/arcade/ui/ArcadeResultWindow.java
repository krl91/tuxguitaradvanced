package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeResult;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UISpinner;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * The result of a run, shown once at its end (spec 003 FR-023, FR-026, T054, T055): the score, its share of the maximum, the best streak, the counts per
 * judgement, the techniques apart, and the replay of the run from the start or from a measure. Nothing is kept: the window holds the immutable result and is
 * dropped when it closes. UI thread only.
 */
public final class ArcadeResultWindow {

	/** The replay of a section: a measure number from 1, with the same settings. */
	public interface Replay {
		void from(int measureNumber);
	}

	private final UIWindow window;

	public ArcadeResultWindow(UIFactory factory, UIWindow parent, ArcadeTexts texts, ArcadeResult result, int firstMeasure, int lastMeasure, int startedMeasure, Replay replay) {
		this(factory, parent, texts, result, firstMeasure, lastMeasure, startedMeasure, replay, new ArrayList<ArcadeResultText.Line>());
	}

	/** @param settingsLines the lines naming the level and the leniency position of the run, shown first (spec 014, FR-010); empty for none */
	public ArcadeResultWindow(UIFactory factory, UIWindow parent, ArcadeTexts texts, ArcadeResult result, int firstMeasure, int lastMeasure, int startedMeasure, Replay replay,
			List<ArcadeResultText.Line> settingsLines) {
		this.window = factory.createWindow(parent, false, true);
		this.window.setText(texts.get("arcade.result.title"));
		UITableLayout layout = new UITableLayout();
		this.window.setLayout(layout);
		int row = 1;
		for (ArcadeResultText.Line line : settingsLines) {
			row = addLine(factory, layout, row, line);
		}
		List<ArcadeResultText.Line> lines = ArcadeResultText.of(result, texts);
		for (ArcadeResultText.Line line : lines) {
			row = addLine(factory, layout, row, line);
		}
		List<ArcadeResultText.Line> techniques = ArcadeResultText.techniques(result, texts);
		if (!techniques.isEmpty()) {
			UILabel title = factory.createLabel(this.window);
			title.setText(texts.get("arcade.result.techniques"));
			layout.set(title, row++, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
			for (ArcadeResultText.Line line : techniques) {
				row = addLine(factory, layout, row, line);
			}
		}
		UITableLayout replayLayout = new UITableLayout(0f);
		UIPanel replayPanel = factory.createPanel(this.window, false);
		replayPanel.setLayout(replayLayout);
		layout.set(replayPanel, row++, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		UIButton again = factory.createButton(replayPanel);
		again.setText(texts.get("arcade.result.replay.start"));
		replayLayout.set(again, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		UILabel from = factory.createLabel(replayPanel);
		from.setText(texts.get("arcade.result.replay.measure"));
		replayLayout.set(from, 1, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		final UISpinner measure = factory.createSpinner(replayPanel);
		measure.setMinimum(Math.max(1, firstMeasure));
		measure.setMaximum(Math.max(Math.max(1, firstMeasure), lastMeasure));
		measure.setValue(Math.max(Math.max(1, firstMeasure), Math.min(startedMeasure, Math.max(firstMeasure, lastMeasure))));
		replayLayout.set(measure, 1, 3, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		UIButton play = factory.createButton(replayPanel);
		play.setText(texts.get("arcade.result.replay.button"));
		replayLayout.set(play, 1, 4, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		UIButton close = factory.createButton(this.window);
		close.setText(texts.get("arcade.result.close"));
		layout.set(close, row, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, true, false, 1, 2, 90f, 28f, null);
		again.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				close();
				replay.from(Math.max(1, firstMeasure));
			}
		});
		play.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				int chosen = measure.getValue();
				close();
				replay.from(chosen);
			}
		});
		close.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				close();
			}
		});
	}

	private int addLine(UIFactory factory, UITableLayout layout, int row, ArcadeResultText.Line line) {
		UILabel label = factory.createLabel(this.window);
		label.setText(line.label());
		layout.set(label, row, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false);
		UILabel value = factory.createLabel(this.window);
		value.setText(line.value());
		layout.set(value, row, 2, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, false, false);
		return row + 1;
	}

	public void open() {
		this.window.pack();
		this.window.open();
	}

	public void close() {
		if (!this.window.isDisposed()) {
			this.window.dispose();
		}
	}

	public boolean isOpen() {
		return !this.window.isDisposed();
	}
}
