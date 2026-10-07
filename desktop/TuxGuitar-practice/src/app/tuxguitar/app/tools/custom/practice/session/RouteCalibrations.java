package app.tuxguitar.app.tools.custom.practice.session;

import java.util.HashMap;
import java.util.Map;

/**
 * The latency compensation of each route (spec 002 T097, FR-054, FR-058): what the learner calibrated or set by hand for an input, an output and an
 * audio format. Looking a route up gives its own compensation or none; a route that changed in any part is another route, so the old calibration is
 * simply not found and the precision of the rhythm is reduced until the new route is calibrated. A calibration is never moved to another route.
 * <p>
 * It is kept in memory and, when a {@link Persistence} is given, written with each change, so that the next run of the application finds it. A text
 * that cannot be read is ignored, not an error: the learner calibrates again.
 */
public final class RouteCalibrations {

	/** Where the calibrations live between two runs: a map from the key of a route to a text. */
	public interface Persistence {
		Map<String, String> load();

		void save(Map<String, String> entries);
	}

	private final Map<AudioRoute, LatencyCompensation> entries = new HashMap<AudioRoute, LatencyCompensation>();
	private final Persistence persistence;

	public RouteCalibrations() {
		this(null);
	}

	public RouteCalibrations(Persistence persistence) {
		this.persistence = persistence;
		if (persistence != null) {
			for (Map.Entry<String, String> entry : persistence.load().entrySet()) {
				try {
					LatencyCompensation compensation = decode(entry.getValue());
					if (compensation != null) {
						this.entries.put(AudioRoute.parse(entry.getKey()), compensation);
					}
				} catch (RuntimeException unreadable) {
					// ignored: the route is simply not calibrated
				}
			}
		}
	}

	public synchronized LatencyCompensation forRoute(AudioRoute route) {
		LatencyCompensation found = (route != null ? this.entries.get(route) : null);
		return found != null ? found : LatencyCompensation.none();
	}

	public synchronized void set(AudioRoute route, LatencyCompensation compensation) {
		if (route == null) {
			return;
		}
		if (compensation == null || !compensation.isCalibrated()) {
			this.entries.remove(route);
		} else {
			this.entries.put(route, compensation);
		}
		save();
	}

	public synchronized void forget(AudioRoute route) {
		set(route, null);
	}

	public synchronized int size() {
		return this.entries.size();
	}

	private void save() {
		if (this.persistence == null) {
			return;
		}
		Map<String, String> out = new HashMap<String, String>();
		for (Map.Entry<AudioRoute, LatencyCompensation> entry : this.entries.entrySet()) {
			out.put(entry.getKey().key(), encode(entry.getValue()));
		}
		this.persistence.save(out);
	}

	static String encode(LatencyCompensation compensation) {
		return compensation.source().name() + ":" + (compensation.offsetNanos() / 1_000_000.0);
	}

	static LatencyCompensation decode(String text) {
		int colon = text.indexOf(':');
		if (colon < 0) {
			return null;
		}
		double millis = Double.parseDouble(text.substring(colon + 1));
		switch (LatencyCompensation.Source.valueOf(text.substring(0, colon))) {
			case MANUAL:
				return LatencyCompensation.manual(millis);
			case CALIBRATED:
				return LatencyCompensation.calibrated(millis);
			default:
				return null;
		}
	}
}
