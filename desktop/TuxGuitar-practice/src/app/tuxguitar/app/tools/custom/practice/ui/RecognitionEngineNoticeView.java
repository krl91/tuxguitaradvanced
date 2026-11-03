package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

import app.tuxguitar.audio.engine.backend.RecognitionEngineNotice;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionException.Reason;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * A line of the practice window that says the native engine could not start and the practice goes on with Java (spec 023, FR-006): each reason once per
 * session, nothing for a refused permission. It never opens a window nor waits for the learner. Call {@link #show} on the user-interface thread.
 */
final class RecognitionEngineNoticeView {

	/** The reasons already shown since TuxGuitar started: one set per application context, in memory only. */
	static final class Session {
		private final Set<Reason> shown = EnumSet.noneOf(Reason.class);

		static Session of(TGContext context) {
			return TGSingletonUtil.getInstance(context, Session.class.getName(), new TGSingletonFactory<Session>() {
				public Session createInstance(TGContext singletonContext) {
					return new Session();
				}
			});
		}

		synchronized boolean firstTime(Reason reason) {
			return this.shown.add(reason);
		}
	}

	private final UILabel label;
	private final Function<String, String> texts;
	private final Session session;
	private final Runnable refit;

	RecognitionEngineNoticeView(UIFactory factory, UIContainer parent, UITableLayout layout, int row, Function<String, String> texts, Session session, Runnable refit) {
		this.texts = texts;
		this.session = session;
		this.refit = refit;
		this.label = factory.createWrapLabel(parent);
		this.label.setText("");
		layout.set(this.label, row, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.label, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);
	}

	void show(RecognitionEngineNotice notice) {
		String key = RecognitionEngineNotices.key(notice.getReason());
		if( key == null || this.label.isDisposed() || !this.session.firstTime(notice.getReason()) ) {
			return;
		}
		this.label.setText(this.texts.apply(key));
		this.refit.run();
	}

	/** Empties the line (the next run starts without it). */
	void clear() {
		if( !this.label.isDisposed() && !getText().isEmpty() ) {
			this.label.setText("");
			this.refit.run();
		}
	}

	String getText() {
		String text = this.label.getText();
		return (text != null ? text : "");
	}

	/** Whether a notice is shown. */
	boolean isShowing() {
		return !getText().isEmpty();
	}
}
