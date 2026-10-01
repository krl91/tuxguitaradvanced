package app.tuxguitar.audio.engine.frontend;

/**
 * Second-order Butterworth high-pass that removes rumble and DC (spec 002, FR-064). The corner is
 * 40 Hz: the lowest guitar string in drop D is about 73 Hz and in standard tuning about 82 Hz, and
 * both pass with less than 0.5 dB of attenuation, while 10 Hz is cut by about 24 dB.
 */
public final class HighPass implements FrontEndStage {

	public static final double CORNER_HZ = 40.0;

	private final double b0;
	private final double b1;
	private final double b2;
	private final double a1;
	private final double a2;
	private double x1;
	private double x2;
	private double y1;
	private double y2;

	public HighPass(int sampleRate) {
		double w0 = 2.0 * Math.PI * CORNER_HZ / sampleRate;
		double q = 1.0 / Math.sqrt(2.0);
		double alpha = Math.sin(w0) / (2.0 * q);
		double cos = Math.cos(w0);
		double a0 = 1.0 + alpha;
		this.b0 = ((1.0 + cos) / 2.0) / a0;
		this.b1 = (-(1.0 + cos)) / a0;
		this.b2 = this.b0;
		this.a1 = (-2.0 * cos) / a0;
		this.a2 = (1.0 - alpha) / a0;
	}

	@Override
	public void process(float[] hop, int length) {
		for( int i = 0 ; i < length ; i ++ ) {
			double x = hop[i];
			double y = this.b0 * x + this.b1 * this.x1 + this.b2 * this.x2 - this.a1 * this.y1 - this.a2 * this.y2;
			this.x2 = this.x1;
			this.x1 = x;
			this.y2 = this.y1;
			this.y1 = y;
			hop[i] = (float) y;
		}
	}

	@Override
	public void reset() {
		this.x1 = 0.0;
		this.x2 = 0.0;
		this.y1 = 0.0;
		this.y2 = 0.0;
	}

	@Override
	public int latencyHops() {
		return 0;
	}
}
