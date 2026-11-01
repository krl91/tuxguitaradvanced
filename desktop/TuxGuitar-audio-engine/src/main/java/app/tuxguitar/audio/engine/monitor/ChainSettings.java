package app.tuxguitar.audio.engine.monitor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The learner's chain: the pedals before the amp, the amp with its cabinet, the pedals after it (spec 022 data-model "ChainSettings").
 * Immutable, compared by value. Its compact text (research R6) is, for example,
 * {@code gate:on,-60,150|amp:crunch,5,5,5,5,6|cab:on|echo:on,380,3,5,30}.
 */
public final class ChainSettings {

	private final List<PedalSettings> before;
	private final AmpSettings amp;
	private final List<PedalSettings> after;

	/** @throws IllegalArgumentException when a kind is there twice or sits in the other section */
	public ChainSettings(List<PedalSettings> before, AmpSettings amp, List<PedalSettings> after) {
		if( amp == null ) {
			throw new IllegalArgumentException("a chain has an amp");
		}
		Set<EffectKind> seen = EnumSet.noneOf(EffectKind.class);
		check(before, EffectKind.Section.BEFORE, seen);
		check(after, EffectKind.Section.AFTER, seen);
		this.before = Collections.unmodifiableList(new ArrayList<PedalSettings>(before));
		this.amp = amp;
		this.after = Collections.unmodifiableList(new ArrayList<PedalSettings>(after));
	}

	private static void check(List<PedalSettings> pedals, EffectKind.Section section, Set<EffectKind> seen) {
		for( PedalSettings pedal : pedals ) {
			if( pedal == null ) {
				throw new IllegalArgumentException("a missing pedal");
			}
			if( pedal.kind().section() != section ) {
				throw new IllegalArgumentException(pedal.kind() + " belongs " + pedal.kind().section() + " the amp");
			}
			if( !seen.add(pedal.kind()) ) {
				throw new IllegalArgumentException(pedal.kind() + " is in the chain twice");
			}
		}
	}

	public List<PedalSettings> before() {
		return this.before;
	}

	public AmpSettings amp() {
		return this.amp;
	}

	public List<PedalSettings> after() {
		return this.after;
	}

	public ChainSettings withAmp(AmpSettings value) {
		return new ChainSettings(this.before, value, this.after);
	}

	public ChainSettings withBefore(List<PedalSettings> value) {
		return new ChainSettings(value, this.amp, this.after);
	}

	public ChainSettings withAfter(List<PedalSettings> value) {
		return new ChainSettings(this.before, this.amp, value);
	}

	/** The pedal of that kind, or null when it is not in the chain. */
	public PedalSettings pedal(EffectKind kind) {
		for( PedalSettings pedal : this.before ) {
			if( pedal.kind() == kind ) {
				return pedal;
			}
		}
		for( PedalSettings pedal : this.after ) {
			if( pedal.kind() == kind ) {
				return pedal;
			}
		}
		return null;
	}

	public String toText() {
		StringBuilder text = new StringBuilder();
		for( PedalSettings pedal : this.before ) {
			append(text, pedal);
		}
		text.append(text.length() == 0 ? "" : "|").append("amp:").append(this.amp.character().textName());
		for( double value : new double[] { this.amp.gain(), this.amp.bass(), this.amp.middle(), this.amp.treble(), this.amp.volume() } ) {
			text.append(',').append(number(value));
		}
		text.append("|cab:").append(this.amp.cabinet() ? "on" : "off");
		for( PedalSettings pedal : this.after ) {
			append(text, pedal);
		}
		return text.toString();
	}

	private static void append(StringBuilder text, PedalSettings pedal) {
		if( text.length() > 0 ) {
			text.append('|');
		}
		text.append(pedal.kind().textName()).append(pedal.enabled() ? ":on" : ":off");
		for( int i = 0 ; i < pedal.kind().knobs().size() ; i++ ) {
			text.append(',').append(number(pedal.value(i)));
		}
	}

	private static String number(double value) {
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	/** The chain of that text, or empty when the text is malformed (the caller then falls back to a ready-made sound). */
	public static Optional<ChainSettings> parse(String text) {
		if( text == null || text.trim().isEmpty() ) {
			return Optional.empty();
		}
		List<PedalSettings> before = new ArrayList<PedalSettings>();
		List<PedalSettings> after = new ArrayList<PedalSettings>();
		AmpSettings amp = null;
		Boolean cabinet = null;
		try {
			for( String element : text.trim().split("\\|", -1) ) {
				int colon = element.indexOf(':');
				if( colon <= 0 ) {
					return Optional.empty();
				}
				String name = element.substring(0, colon).trim();
				String[] fields = element.substring(colon + 1).split(",", -1);
				if( name.equals("amp") ) {
					if( amp != null || fields.length != 6 ) {
						return Optional.empty();
					}
					AmpSettings.Character character = AmpSettings.Character.ofText(fields[0].trim());
					if( character == null ) {
						return Optional.empty();
					}
					amp = new AmpSettings(character, value(fields[1]), value(fields[2]), value(fields[3]), value(fields[4]), value(fields[5]), true);
				} else if( name.equals("cab") ) {
					if( amp == null || cabinet != null || fields.length != 1 ) {
						return Optional.empty();
					}
					cabinet = onOff(fields[0]);
					if( cabinet == null ) {
						return Optional.empty();
					}
				} else {
					EffectKind kind = EffectKind.ofText(name);
					if( kind == null || fields.length != kind.knobs().size() + 1 ) {
						return Optional.empty();
					}
					Boolean enabled = onOff(fields[0]);
					if( enabled == null ) {
						return Optional.empty();
					}
					double[] values = new double[kind.knobs().size()];
					for( int i = 0 ; i < values.length ; i++ ) {
						values[i] = value(fields[i + 1]);
					}
					PedalSettings pedal = PedalSettings.of(kind, enabled, values);
					if( amp == null ) {
						before.add(pedal);
					} else {
						if( cabinet == null ) {
							return Optional.empty();
						}
						after.add(pedal);
					}
				}
			}
			if( amp == null || cabinet == null ) {
				return Optional.empty();
			}
			return Optional.of(new ChainSettings(before, amp.withCabinet(cabinet), after));
		} catch (IllegalArgumentException exception) {
			// a number that is not one, or a kind twice or in the wrong section
			return Optional.empty();
		}
	}

	private static double value(String field) {
		return Double.parseDouble(field.trim());
	}

	private static Boolean onOff(String field) {
		String value = field.trim();
		if( value.equals("on") ) {
			return Boolean.TRUE;
		}
		if( value.equals("off") ) {
			return Boolean.FALSE;
		}
		return null;
	}

	@Override
	public boolean equals(Object other) {
		if( !(other instanceof ChainSettings) ) {
			return false;
		}
		ChainSettings chain = (ChainSettings) other;
		return this.before.equals(chain.before) && this.amp.equals(chain.amp) && this.after.equals(chain.after);
	}

	@Override
	public int hashCode() {
		return (this.before.hashCode() * 31 + this.amp.hashCode()) * 31 + this.after.hashCode();
	}

	@Override
	public String toString() {
		return toText();
	}
}
