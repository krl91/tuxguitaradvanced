package app.tuxguitar.app.tools.custom.practice.arcade.stress;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;

import javax.sound.midi.MidiSystem;

import app.tuxguitar.document.TGDocumentManager;
import app.tuxguitar.player.base.MidiChannel;
import app.tuxguitar.player.base.MidiOutputPort;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.player.base.MidiPlayerException;
import app.tuxguitar.player.base.MidiSynthesizer;
import app.tuxguitar.player.impl.jsa.sequencer.MidiSequencerImpl;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.thread.TGMultiThreadHandler;

/**
 * The real {@link MidiPlayer} with the real {@code javax.sound} sequencer and a silent output (spec 003 T047): the sequencer runs in real time and the
 * player, its clock, its count-in and its notifications are the product's own; only the sound is absent, and the number of notes the sequencer sent to
 * the channels is counted so that a run can show the backing really played.
 */
public final class SilentMidi {

	public final TGContext context = new TGContext();
	public final MidiPlayer player;
	/** The note-on messages the sequencer delivered to the (silent) channels. */
	public final AtomicLong notesSent = new AtomicLong();

	public SilentMidi(TGSong song) throws Exception {
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
		TGDocumentManager.getInstance(this.context).setSong(song);
		this.player = MidiPlayer.getInstance(this.context);
		this.player.loadSequencer(new MidiSequencerImpl(MidiSystem.getSequencer(false)));
		this.player.loadOutputPort(new SilentPort());
		this.player.getCountDown().setEnabled(false);
	}

	private MidiChannel channel() {
		return (MidiChannel) Proxy.newProxyInstance(MidiChannel.class.getClassLoader(), new Class<?>[] { MidiChannel.class }, (proxy, method, args) -> {
			if (method.getName().equals("sendNoteOn")) {
				this.notesSent.incrementAndGet();
			}
			return null;
		});
	}

	private final class SilentPort implements MidiOutputPort {
		private final MidiSynthesizer synthesizer = (MidiSynthesizer) Proxy.newProxyInstance(MidiSynthesizer.class.getClassLoader(),
				new Class<?>[] { MidiSynthesizer.class }, (proxy, method, args) -> {
					switch (method.getName()) {
						case "openChannel":
							return channel();
						case "isChannelOpen":
							return Boolean.TRUE;
						case "isBusy":
							return Boolean.FALSE;
						default:
							return null;
					}
				});

		@Override
		public MidiSynthesizer getSynthesizer() throws MidiPlayerException {
			return this.synthesizer;
		}

		@Override
		public String getKey() {
			return "silent";
		}

		@Override
		public String getName() {
			return "Silent output";
		}

		@Override
		public void open() {
		}

		@Override
		public void close() {
		}

		@Override
		public void check() {
		}
	}
}
