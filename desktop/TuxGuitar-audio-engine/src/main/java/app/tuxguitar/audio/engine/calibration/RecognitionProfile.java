package app.tuxguitar.audio.engine.calibration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import app.tuxguitar.audio.engine.settings.ProfileParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.SettingsOverlay;

/**
 * The recognition settings chosen for one device (spec 013, data-model): values of the profile parameters only, with the facts of the calibration that made
 * them. A profile whose origin is {@link Origin#DEFAULT_CHOSEN} holds no value: the device then follows the default configuration, whatever it is edited to.
 */
public final class RecognitionProfile implements SettingsOverlay {

	public enum Origin {
		/** found by the search on a recording */
		SEARCHED,
		/** the player chose the default configuration for this device */
		DEFAULT_CHOSEN
	}

	/** The layout of what is stored; a stored profile of another layout is read as no profile. */
	public static final int LAYOUT = 1;

	private final DeviceKey key;
	private final int sampleRate;
	private final long dateMillis;
	private final Map<RecognitionParameter.Number, Double> values;
	private final RecognitionRate foundRate;
	private final RecognitionRate defaultRate;
	private final FrontEndState frontEnd;
	private final Origin origin;

	public RecognitionProfile(DeviceKey key, int sampleRate, long dateMillis, Map<RecognitionParameter.Number, Double> values, RecognitionRate foundRate,
			RecognitionRate defaultRate, FrontEndState frontEnd, Origin origin) {
		if (key == null || values == null || frontEnd == null || origin == null) {
			throw new IllegalArgumentException("key, values, front end and origin are required");
		}
		if (origin == Origin.SEARCHED && (foundRate == null || defaultRate == null)) {
			throw new IllegalArgumentException("a searched profile carries the rates found and of the default");
		}
		for (Map.Entry<RecognitionParameter.Number, Double> entry : values.entrySet()) {
			if (!ProfileParameters.contains(entry.getKey())) {
				throw new IllegalArgumentException(entry.getKey().getKey() + " is not a profile parameter");
			}
			if (entry.getValue() == null || !ProfileParameters.isValid(entry.getKey(), entry.getValue())) {
				throw new IllegalArgumentException(entry.getKey().getKey() + ": " + entry.getValue() + " is outside its bounds");
			}
		}
		Map<RecognitionParameter.Number, Double> ordered = new LinkedHashMap<RecognitionParameter.Number, Double>();
		for (RecognitionParameter.Number parameter : ProfileParameters.LIST) {
			if (values.containsKey(parameter)) {
				ordered.put(parameter, values.get(parameter));
			}
		}
		this.key = key;
		this.sampleRate = sampleRate;
		this.dateMillis = dateMillis;
		this.values = Collections.unmodifiableMap(ordered);
		this.foundRate = foundRate;
		this.defaultRate = defaultRate;
		this.frontEnd = frontEnd;
		this.origin = origin;
	}

	/** The profile of a device the player left on the default configuration: no value, no rate. */
	public static RecognitionProfile defaultChosen(DeviceKey key, int sampleRate, long dateMillis, FrontEndState frontEnd) {
		return new RecognitionProfile(key, sampleRate, dateMillis, new LinkedHashMap<RecognitionParameter.Number, Double>(), null, null, frontEnd, Origin.DEFAULT_CHOSEN);
	}

	public DeviceKey getKey() {
		return this.key;
	}

	public int getSampleRate() {
		return this.sampleRate;
	}

	public long getDateMillis() {
		return this.dateMillis;
	}

	public Map<RecognitionParameter.Number, Double> getValues() {
		return this.values;
	}

	/** The rate of the settings found on the calibration recording; null for {@link Origin#DEFAULT_CHOSEN}. */
	public RecognitionRate getFoundRate() {
		return this.foundRate;
	}

	/** The rate of the default settings on the same recording; null for {@link Origin#DEFAULT_CHOSEN}. */
	public RecognitionRate getDefaultRate() {
		return this.defaultRate;
	}

	public FrontEndState getFrontEnd() {
		return this.frontEnd;
	}

	public Origin getOrigin() {
		return this.origin;
	}

	/** A profile made at one sample rate is not applied at another; it applies while the capture rate is not known (0). */
	public boolean appliesAt(int captureSampleRate) {
		return captureSampleRate <= 0 || captureSampleRate == this.sampleRate;
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof RecognitionProfile)) {
			return false;
		}
		RecognitionProfile profile = (RecognitionProfile) other;
		return this.key.equals(profile.key) && this.sampleRate == profile.sampleRate && this.dateMillis == profile.dateMillis && this.values.equals(profile.values)
				&& java.util.Objects.equals(this.foundRate, profile.foundRate) && java.util.Objects.equals(this.defaultRate, profile.defaultRate)
				&& this.frontEnd.equals(profile.frontEnd) && this.origin == profile.origin;
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(this.key, this.sampleRate, this.dateMillis, this.values, this.foundRate, this.defaultRate, this.frontEnd, this.origin);
	}

	@Override
	public RecognitionSettings applyTo(RecognitionSettings base) {
		return ProfileParameters.merge(base, this.values);
	}
}
