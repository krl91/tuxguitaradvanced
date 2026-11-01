package app.tuxguitar.audio.engine.monitor;

/** A pedal of the chain: an effect set from {@link PedalSettings}. {@link #set} only stores targets (real-time safe, no allocation). */
public interface Pedal extends Effect {

	void set(PedalSettings settings);
}
