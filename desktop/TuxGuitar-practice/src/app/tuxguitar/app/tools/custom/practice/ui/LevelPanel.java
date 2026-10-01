package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIProgressBar;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * Live input level and level-check result (FR-037, FR-038) with manual gain guidance and an
 * explicit "check again" (FR-039, FR-040). The operating-system gain is never changed (FR-041,
 * FR-042): Java Sound exposes no usable gain control on the validated setup (research S2). Since
 * spec 002 the panel also hosts the software input gain ({@link GainControl}).
 */
public final class LevelPanel {

	private static final long UI_INTERVAL_NANOS = 33_000_000L;
	private static final int METER_FLOOR_DB = -60;

	private final TGContext context;
	private final AudioEngine engine;
	private final UIPanel panel;
	private final UIProgressBar meter;
	private final UILabel levelLabel;
	private final UILabel statusLabel;
	private final UILabel guidanceLabel;
	private final GainControl gainControl;
	private final AnalysisService.Listener frameListener;
	private final AtomicReference<AnalysisFrame> latestFrame = new AtomicReference<AnalysisFrame>();
	private final AtomicBoolean uiPending = new AtomicBoolean();
	private volatile long lastUiNanos;
	private boolean connected;
	private Runnable contentListener;

	public LevelPanel(TGContext context, AudioEngine engine, UIFactory uiFactory, UIContainer parent) {
		this.context = context;
		this.engine = engine;
		this.panel = uiFactory.createPanel(parent, false);
		UITableLayout layout = new UITableLayout();
		this.panel.setLayout(layout);

		this.meter = uiFactory.createProgressBar(this.panel);
		this.meter.setMinimum(0);
		this.meter.setMaximum(-METER_FLOOR_DB);
		layout.set(this.meter, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.levelLabel = uiFactory.createLabel(this.panel);
		layout.set(this.levelLabel, 1, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);

		this.statusLabel = uiFactory.createWrapLabel(this.panel);
		layout.set(this.statusLabel, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		layout.set(this.statusLabel, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);
		this.guidanceLabel = uiFactory.createWrapLabel(this.panel);
		layout.set(this.guidanceLabel, 3, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		layout.set(this.guidanceLabel, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);

		UIButton checkAgain = uiFactory.createButton(this.panel);
		checkAgain.setText(TuxGuitar.getProperty("practice.level.check-again"));
		checkAgain.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				LevelPanel.this.engine.runLevelCheck();
			}
		});
		layout.set(checkAgain, 4, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false, 1, 2);

		// the in-application input gain, shared with the tuner (spec 002, FR-060)
		this.gainControl = new GainControl(context, engine, uiFactory, this.panel);
		layout.set(this.gainControl.getControl(), 5, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);

		this.frameListener = this::frameReceived;
	}

	/** Called after the panel's content changed size, so the window can refit (UI thread). */
	public void setContentListener(Runnable contentListener) {
		this.contentListener = contentListener;
		this.gainControl.setContentListener(contentListener);
	}

	public UIPanel getControl() {
		return this.panel;
	}

	/** Starts the live meter; the engine's capture must be held by the window. */
	public void connect() {
		if( !this.connected ) {
			this.connected = true;
			this.gainControl.connect();
			this.engine.subscribe(this.frameListener);
		}
	}

	public void disconnect() {
		if( this.connected ) {
			this.connected = false;
			this.gainControl.disconnect();
			this.engine.unsubscribe(this.frameListener);
		}
	}

	/** Shows the level-check result with guidance (UI thread). */
	public void setAssessment(InputAssessment assessment) {
		// a new input resets the gain: show it
		this.gainControl.refresh();
		String reason = TuxGuitar.getProperty("practice.input.reason." + assessment.getReason().name());
		this.statusLabel.setText(TuxGuitar.getProperty("practice.input.status." + assessment.getStatus().name(), new String[] { reason }));
		String guidanceKey = "practice.level.guidance." + assessment.getReason().name();
		boolean needsGuidance = (assessment.getStatus() == InputAssessment.Status.SUBOPTIMAL || assessment.getStatus() == InputAssessment.Status.UNUSABLE);
		this.guidanceLabel.setText(needsGuidance ? TuxGuitar.getProperty(guidanceKey) : "");
		this.panel.layout();
		if( this.contentListener != null ) {
			this.contentListener.run();
		}
	}

	/** Analysis thread: keeps the latest frame and schedules at most ~30 UI updates per second. */
	private void frameReceived(AnalysisFrame frame) {
		this.latestFrame.set(frame);
		long now = System.nanoTime();
		if( now - this.lastUiNanos >= UI_INTERVAL_NANOS && this.uiPending.compareAndSet(false, true) ) {
			this.lastUiNanos = now;
			TGSynchronizer.getInstance(this.context).executeLater(this::updateMeter);
		}
	}

	private void updateMeter() {
		this.uiPending.set(false);
		AnalysisFrame frame = this.latestFrame.get();
		if( frame == null || this.panel.isDisposed() ) {
			return;
		}
		int value = (int) Math.round(Math.max(METER_FLOOR_DB, Math.min(0.0, frame.getRmsDbfs())) - METER_FLOOR_DB);
		this.meter.setValue(value);
		this.levelLabel.setText(TuxGuitar.getProperty("practice.level." + frame.getLevelState().name()));
		this.gainControl.showWarning(frame);
	}
}
