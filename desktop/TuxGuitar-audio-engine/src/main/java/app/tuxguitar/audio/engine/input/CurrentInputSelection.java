package app.tuxguitar.audio.engine.input;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.AudioFormat;

import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/** Shared per-context selection used by every audio-engine consumer. */
public final class CurrentInputSelection {

	public interface Listener {
		void selectionChanged(Snapshot snapshot);
	}

	public static final class Snapshot {
		private final AudioInputDescriptor input;
		private final AudioFormat format;
		private final InputAssessment assessment;
		private final long revision;
		private final long inputRevision;

		private Snapshot(AudioInputDescriptor input, AudioFormat format, InputAssessment assessment, long revision, long inputRevision) {
			this.input = input;
			this.format = format;
			this.assessment = assessment;
			this.revision = revision;
			this.inputRevision = inputRevision;
		}

		public AudioInputDescriptor getInput() {
			return this.input;
		}

		public AudioFormat getFormat() {
			return this.format;
		}

		public InputAssessment getAssessment() {
			return this.assessment;
		}

		public long getRevision() {
			return this.revision;
		}

		/** Changes only when the selected device/channel changes or is explicitly reselected. */
		public long getInputRevision() {
			return this.inputRevision;
		}
	}

	private final TGContext context;
	private final Object stateLock;
	private final List<Listener> listeners;
	private AudioInputDescriptor input;
	private AudioFormat format;
	private InputAssessment assessment;
	private long revision;
	private long inputRevision;

	private CurrentInputSelection(TGContext context) {
		this.context = context;
		this.stateLock = new Object();
		this.listeners = new CopyOnWriteArrayList<Listener>();
		this.assessment = InputAssessment.UNCHECKED;
	}

	public void select(AudioInputDescriptor input, AudioFormat format) {
		if (input == null) {
			throw new IllegalArgumentException("input is required");
		}
		Snapshot snapshot;
		synchronized (this.stateLock) {
			this.input = input;
			this.format = format;
			this.assessment = InputAssessment.UNCHECKED;
			this.revision++;
			this.inputRevision++;
			snapshot = snapshotLocked();
		}
		notifyListeners(snapshot);
	}

	public void clear() {
		Snapshot snapshot;
		synchronized (this.stateLock) {
			this.input = null;
			this.format = null;
			this.assessment = InputAssessment.UNCHECKED;
			this.revision++;
			this.inputRevision++;
			snapshot = snapshotLocked();
		}
		notifyListeners(snapshot);
	}

	public void updateAssessment(InputAssessment assessment) {
		if (assessment == null) {
			throw new IllegalArgumentException("assessment is required");
		}
		Snapshot snapshot;
		synchronized (this.stateLock) {
			this.assessment = assessment;
			this.revision++;
			snapshot = snapshotLocked();
		}
		notifyListeners(snapshot);
	}

	public Snapshot snapshot() {
		synchronized (this.stateLock) {
			return snapshotLocked();
		}
	}

	public void addListener(Listener listener) {
		this.listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		this.listeners.remove(listener);
	}

	private Snapshot snapshotLocked() {
		return new Snapshot(this.input, this.format, this.assessment, this.revision, this.inputRevision);
	}

	private void notifyListeners(Snapshot snapshot) {
		TGThreadManager.getInstance(this.context).start(() -> {
			for (Listener listener : this.listeners) {
				try {
					listener.selectionChanged(snapshot);
				} catch (RuntimeException exception) {
					// One consumer must not prevent delivery to the others.
				}
			}
		});
	}

	public static CurrentInputSelection getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, CurrentInputSelection.class.getName(), new TGSingletonFactory<CurrentInputSelection>() {
			@Override
			public CurrentInputSelection createInstance(TGContext singletonContext) {
				return new CurrentInputSelection(singletonContext);
			}
		});
	}
}
