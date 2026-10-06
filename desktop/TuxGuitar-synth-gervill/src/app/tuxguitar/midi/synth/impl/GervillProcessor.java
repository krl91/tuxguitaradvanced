package app.tuxguitar.midi.synth.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.Patch;
import javax.sound.midi.Receiver;
import javax.sound.sampled.AudioInputStream;

import app.tuxguitar.midi.synth.TGAudioBuffer;
import app.tuxguitar.midi.synth.TGAudioLine;
import app.tuxguitar.midi.synth.TGMidiProcessor;
import app.tuxguitar.util.TGContext;

import media.sound.AudioSynthesizer;
import media.sound.SoftSynthesizer;

public class GervillProcessor implements TGMidiProcessor {

	private static final String SYNTH_PROGRAM_PARAM = "gervil.program";
	private static final String SYNTH_CHANNEL_MODE_PARAM = "gervil.channel.mode";
	private static final String SYNTH_SOUNDBANK_PATH_PARAM = "gervil.soundbank.path";

	private static final String SYNTH_LOAD_DEFAULT_SOUNDBANK_PARAM = "load default soundbank";
	private static final String SYNTH_MIDI_CHANNELS_PARAM = "midi channels";

	private TGContext context;
	/**
	 * Guards the life cycle: the soundbank of an instrument is built on another thread, so its
	 * answer can come back after {@link #close()} or after a newer {@link #loadInstrument()}.
	 * {@code generation} identifies the current synthesizer session; an answer of an older one
	 * is ignored.
	 */
	private final Object lifecycle = new Object();
	private int generation;
	private AudioSynthesizer synth;
	private AudioInputStream stream;
	private Receiver receiver;
	private GervillProgram program;
	private GervillSoundbankFactory soundbankFactory;
	private byte[] buffer;
	private float[][] outputs;

	public GervillProcessor(TGContext context) {
		this(context, new GervillSoundbankFactory());
	}

	/** Test point: the factory decides when, and if, the instrument is created. */
	GervillProcessor(TGContext context, GervillSoundbankFactory soundbankFactory) {
		this.context = context;
		this.synth = new SoftSynthesizer();
		this.program = new GervillProgram();
		this.soundbankFactory = soundbankFactory;
		this.buffer = new byte[TGAudioBuffer.CHANNELS * TGAudioBuffer.BUFFER_SIZE];
		this.outputs = new float[TGAudioBuffer.CHANNELS][TGAudioBuffer.BUFFER_SIZE / 2];
		for(int i = 0; i < this.outputs.length; i++) {
			this.outputs[i] = new float[TGAudioBuffer.BUFFER_SIZE / 2];
		}
		this.program.setProgram(-1);
	}

	public void close() {
		synchronized (this.lifecycle) {
			this.generation ++;
			if( this.receiver != null ){
				this.receiver.close();
			}
			if( this.synth != null && this.synth.isOpen() ){
				this.synth.close();
			}
			this.stream = null;
			this.receiver = null;
			this.synth = null;
			this.buffer = null;
		}
	}

	public boolean isOpen() {
		return (this.buffer != null );
	}

	public boolean isBusy() {
		return (this.isOpen() && this.soundbankFactory.isBusy());
	}

	public void finalize() {
		this.close();
	}

	public void fillBuffer(TGAudioBuffer buffer) {
		try {
			if( this.stream != null ) {
				this.stream.read(buffer.getBuffer());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void loadProgram(GervillProgram program) {
		synchronized (this.lifecycle) {
			if(!this.program.equals(program)) {
				this.program.copyFrom(program);
				this.loadInstrument();
			}
		}
	}

	public void loadInstrument() {
		synchronized (this.lifecycle) {
			if( this.synth == null ) {
				// closed: a closed processor stays closed
				return;
			}
			try {
				// session + synthesizer + program stay together: the thread that builds the soundbank
				// gets its own copy, the program of this processor changes with the next request
				final int session = ++ this.generation;
				final AudioSynthesizer target = this.synth;
				final GervillProgram requested = new GervillProgram();
				requested.copyFrom(this.program);
				if( target.isOpen()) {
					target.close();
				}
				this.stream = target.openStream(TGAudioLine.AUDIO_FORMAT, this.createSynthInfo());
				this.receiver = target.getReceiver();

				this.soundbankFactory.create(this.context, requested, new GervillSoundbankCallback() {
					public void onCreate(Instrument instrument) {
						GervillProcessor.this.loadInstrument(instrument, session, target);
					}
				});
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public void loadInstrument(Instrument instrument) {
		synchronized (this.lifecycle) {
			if( this.synth != null ) {
				this.loadInstrument(instrument, this.generation, this.synth);
			}
		}
	}

	/**
	 * Loads the instrument only if the session that asked for it is still the current one, on the
	 * same synthesizer, still open. A stale answer (closed processor, newer program) is ignored.
	 */
	void loadInstrument(Instrument instrument, int session, AudioSynthesizer target) {
		synchronized (this.lifecycle) {
			if( session != this.generation || target != this.synth || this.synth == null || !this.synth.isOpen() ) {
				return;
			}
			this.synth.loadInstrument(instrument);

			Patch patch = instrument.getPatch();
			for(MidiChannel midiChannel : this.synth.getChannels()) {
				midiChannel.programChange(patch.getBank(), patch.getProgram());
			}
		}
	}

	/** Test point: what the synthesizer holds now (nothing once closed). */
	Instrument[] loadedInstruments() {
		synchronized (this.lifecycle) {
			return (this.synth != null ? this.synth.getLoadedInstruments() : new Instrument[0]);
		}
	}

	public Map<String, Object> createSynthInfo(){
		Map<String, Object> map = new HashMap<String, Object>();
		map.put(SYNTH_LOAD_DEFAULT_SOUNDBANK_PARAM, false);
		if( this.program.getChannelMode() == GervillProgram.CHANNEL_MODE_SINGLE ) {
			map.put(SYNTH_MIDI_CHANNELS_PARAM, 1);
		}
		else if( this.program.getChannelMode() == GervillProgram.CHANNEL_MODE_BEND ) {
			map.put(SYNTH_MIDI_CHANNELS_PARAM, 2);
		}
		else if( this.program.getChannelMode() == GervillProgram.CHANNEL_MODE_VOICE ) {
			map.put(SYNTH_MIDI_CHANNELS_PARAM, 16);
		}
		return map;
	}

	public void storeParameters(Map<String, String> parameters) {
		parameters.put(SYNTH_PROGRAM_PARAM, this.program.getBank() + ":" + this.program.getProgram());
		parameters.put(SYNTH_CHANNEL_MODE_PARAM, Integer.toString(this.program.getChannelMode()));
		parameters.put(SYNTH_SOUNDBANK_PATH_PARAM, this.program.getSoundbankPath());
	}

	public void restoreParameters(Map<String, String> parameters) {
		try {
			GervillProgram program = new GervillProgram();

			String programParam = parameters.get(SYNTH_PROGRAM_PARAM);
			String channelModeParam = parameters.get(SYNTH_CHANNEL_MODE_PARAM);
			String soundbankPathParam = parameters.get(SYNTH_SOUNDBANK_PATH_PARAM);

			if( programParam != null ) {
				String[] parts = programParam.trim().split(":");
				if( parts.length == 2 ) {
					program.setBank(Integer.valueOf(parts[0].trim()));
					program.setProgram(Integer.valueOf(parts[1].trim()));
				}
			}
			if( channelModeParam != null ) {
				program.setChannelMode(Integer.valueOf(channelModeParam.trim()));
			}
			program.setSoundbankPath(soundbankPathParam);

			this.loadProgram(program);
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
	}

	public void sendNoteOn(int key, int velocity, int voice, boolean bendMode) {
		this.synth.getChannels()[resolveChannel(voice, bendMode)].noteOn(key, velocity);
	}

	public void sendNoteOff(int key, int velocity, int voice, boolean bendMode) {
		this.synth.getChannels()[resolveChannel(voice, bendMode)].noteOff(key, velocity);
	}

	public void sendPitchBend(int value, int voice, boolean bendMode) {
		this.synth.getChannels()[resolveChannel(voice, bendMode)].setPitchBend(value * 128);
	}

	public void sendControlChange(int controller, int value) {
		for(MidiChannel midiChannel : this.synth.getChannels()) {
			midiChannel.controlChange(controller, value);
		}
	}

	public int resolveChannel(int voice, boolean bendMode) {
		if( this.program.getChannelMode() == GervillProgram.CHANNEL_MODE_BEND ) {
			return (bendMode ? 1 : 0);
		}
		if( this.program.getChannelMode() == GervillProgram.CHANNEL_MODE_VOICE ) {
			return (voice >= 0 && voice < 9 ? voice : 10);
		}
		return 0;
	}

	public GervillProgram getProgram() {
		return this.program;
	}
}
