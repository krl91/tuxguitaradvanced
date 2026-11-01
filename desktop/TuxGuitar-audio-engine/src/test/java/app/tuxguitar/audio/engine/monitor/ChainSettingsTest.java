package app.tuxguitar.audio.engine.monitor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The settings of the chain (spec 022, data-model "EffectKind", "PedalSettings", "ChainSettings"; task T004). */
public class ChainSettingsTest {

	private static void knob(EffectKind kind, String key, double min, double max, String unit, double defaultValue) {
		Knob knob = kind.knob(key);
		Assertions.assertNotNull(knob, kind + " has no knob " + key);
		Assertions.assertEquals(min, knob.min(), 1e-9, kind + "." + key + " min");
		Assertions.assertEquals(max, knob.max(), 1e-9, kind + "." + key + " max");
		Assertions.assertEquals(unit, knob.unit(), kind + "." + key + " unit");
		Assertions.assertEquals(defaultValue, knob.defaultValue(), 1e-9, kind + "." + key + " default");
	}

	@Test
	public void everyKindHasTheKnobsOfTheDataModel() {
		knob(EffectKind.GATE, "threshold", -90, -20, "dB", -60);
		knob(EffectKind.GATE, "release", 20, 1000, "ms", 150);
		knob(EffectKind.COMPRESSOR, "sustain", 0, 10, "", 5);
		knob(EffectKind.COMPRESSOR, "attack", 1, 100, "ms", 10);
		knob(EffectKind.COMPRESSOR, "level", 0, 10, "", 5);
		knob(EffectKind.OVERDRIVE, "drive", 0, 10, "", 5);
		knob(EffectKind.OVERDRIVE, "tone", 0, 10, "", 5);
		knob(EffectKind.OVERDRIVE, "level", 0, 10, "", 5);
		knob(EffectKind.DISTORTION, "gain", 0, 10, "", 6);
		knob(EffectKind.DISTORTION, "tone", 0, 10, "", 5);
		knob(EffectKind.DISTORTION, "level", 0, 10, "", 5);
		knob(EffectKind.CHORUS, "rate", 0.1, 5, "Hz", 0.8);
		knob(EffectKind.CHORUS, "depth", 0, 10, "", 5);
		knob(EffectKind.CHORUS, "mix", 0, 100, "%", 50);
		knob(EffectKind.ECHO, "time", 50, 1500, "ms", 380);
		knob(EffectKind.ECHO, "repeats", 0, 10, "", 3);
		knob(EffectKind.ECHO, "tone", 0, 10, "", 5);
		knob(EffectKind.ECHO, "mix", 0, 100, "%", 30);
		knob(EffectKind.REVERB, "size", 0, 10, "", 5);
		knob(EffectKind.REVERB, "damping", 0, 10, "", 5);
		knob(EffectKind.REVERB, "mix", 0, 100, "%", 25);
		int[] counts = { 2, 3, 3, 3, 3, 4, 3 };
		EffectKind[] kinds = { EffectKind.GATE, EffectKind.COMPRESSOR, EffectKind.OVERDRIVE, EffectKind.DISTORTION, EffectKind.CHORUS,
				EffectKind.ECHO, EffectKind.REVERB };
		Assertions.assertEquals(kinds.length, EffectKind.values().length);
		for( int i = 0 ; i < kinds.length ; i++ ) {
			Assertions.assertEquals(counts[i], kinds[i].knobs().size(), kinds[i] + " knob count");
		}
		for( EffectKind kind : new EffectKind[] { EffectKind.GATE, EffectKind.COMPRESSOR, EffectKind.OVERDRIVE, EffectKind.DISTORTION } ) {
			Assertions.assertEquals(EffectKind.Section.BEFORE, kind.section(), kind + " section");
		}
		for( EffectKind kind : new EffectKind[] { EffectKind.CHORUS, EffectKind.ECHO, EffectKind.REVERB } ) {
			Assertions.assertEquals(EffectKind.Section.AFTER, kind.section(), kind + " section");
		}
	}

	@Test
	public void aValueOutsideItsRangeIsBroughtBackIntoIt() {
		PedalSettings echo = PedalSettings.defaults(EffectKind.ECHO);
		Assertions.assertEquals(380, echo.value("time"), 1e-9);
		Assertions.assertEquals(1500, echo.with("time", 9000).value("time"), 1e-9);
		Assertions.assertEquals(50, echo.with("time", -3).value("time"), 1e-9);
		Assertions.assertEquals(0, echo.with("mix", Double.NaN).value("mix"), 1e-9, "a value that is not a number becomes the minimum");
		Assertions.assertEquals(380, echo.value("time"), 1e-9, "immutable");
		Assertions.assertThrows(IllegalArgumentException.class, () -> echo.with("drive", 3));
		Assertions.assertFalse(echo.withEnabled(false).enabled());
		Assertions.assertTrue(echo.enabled());
		AmpSettings amp = AmpSettings.defaults().withGain(14).withTreble(-2);
		Assertions.assertEquals(10, amp.gain(), 1e-9);
		Assertions.assertEquals(0, amp.treble(), 1e-9);
	}

	@Test
	public void aKindTwiceOrInTheWrongSectionIsRefused() {
		PedalSettings gate = PedalSettings.defaults(EffectKind.GATE);
		PedalSettings echo = PedalSettings.defaults(EffectKind.ECHO);
		AmpSettings amp = AmpSettings.defaults();
		Assertions.assertThrows(IllegalArgumentException.class, () -> new ChainSettings(Arrays.asList(gate, gate), amp, Collections.emptyList()));
		Assertions.assertThrows(IllegalArgumentException.class, () -> new ChainSettings(Arrays.asList(echo), amp, Collections.emptyList()));
		Assertions.assertThrows(IllegalArgumentException.class, () -> new ChainSettings(Collections.emptyList(), amp, Arrays.asList(gate)));
		Assertions.assertThrows(IllegalArgumentException.class,
				() -> new ChainSettings(Collections.emptyList(), amp, Arrays.asList(echo, echo.withEnabled(false))));
		ChainSettings fine = new ChainSettings(Arrays.asList(gate), amp, Arrays.asList(echo));
		Assertions.assertEquals(1, fine.before().size());
		Assertions.assertEquals(1, fine.after().size());
	}

	@Test
	public void theTextOfEachReadyMadeChainReadsBackEqual() {
		List<ReadyMadeChains.Named> all = ReadyMadeChains.all();
		Assertions.assertEquals(Arrays.asList("Clean", "Crunch", "Lead", "Clean echo"),
				Arrays.asList(all.get(0).name(), all.get(1).name(), all.get(2).name(), all.get(3).name()));
		for( ReadyMadeChains.Named named : all ) {
			String text = named.chain().toText();
			Optional<ChainSettings> parsed = ChainSettings.parse(text);
			Assertions.assertTrue(parsed.isPresent(), named.name() + ": " + text);
			Assertions.assertEquals(named.chain(), parsed.get(), named.name() + ": " + text);
			Assertions.assertEquals(named.chain().hashCode(), parsed.get().hashCode());
		}
		Assertions.assertEquals("gate:on,-60,150|amp:crunch,6,6,5,6,6|cab:on", ReadyMadeChains.CRUNCH.toText());
	}

	@Test
	public void theReadyMadeChainsAreTheDataModelTable() {
		ChainSettings clean = ReadyMadeChains.CLEAN;
		Assertions.assertEquals(-70, clean.before().get(0).value("threshold"), 1e-9);
		Assertions.assertEquals(AmpSettings.Character.CLEAN, clean.amp().character());
		Assertions.assertEquals(EffectKind.REVERB, clean.after().get(0).kind());
		ChainSettings lead = ReadyMadeChains.LEAD;
		Assertions.assertEquals(Arrays.asList(EffectKind.GATE, EffectKind.COMPRESSOR), kinds(lead.before()));
		Assertions.assertEquals(AmpSettings.Character.HIGH_GAIN, lead.amp().character());
		Assertions.assertEquals(7, lead.amp().gain(), 1e-9);
		Assertions.assertEquals(Arrays.asList(EffectKind.ECHO), kinds(lead.after()));
		Assertions.assertEquals(25, lead.after().get(0).value("mix"), 1e-9);
		ChainSettings cleanEcho = ReadyMadeChains.CLEAN_ECHO;
		Assertions.assertEquals(Arrays.asList(EffectKind.CHORUS, EffectKind.ECHO, EffectKind.REVERB), kinds(cleanEcho.after()));
		Assertions.assertEquals(450, cleanEcho.after().get(1).value("time"), 1e-9);
		for( ReadyMadeChains.Named named : ReadyMadeChains.all() ) {
			Assertions.assertTrue(named.chain().amp().cabinet(), named.name() + " has its cabinet on");
		}
	}

	private static List<EffectKind> kinds(List<PedalSettings> pedals) {
		EffectKind[] kinds = new EffectKind[pedals.size()];
		for( int i = 0 ; i < kinds.length ; i++ ) {
			kinds[i] = pedals.get(i).kind();
		}
		return Arrays.asList(kinds);
	}

	@Test
	public void malformedTextsGiveNothing() {
		for( String text : new String[] { "", "amp:loud", "echo:on,abc", "amp:clean,5,5,5,5,5|cab:on|fuzz:on,5", "echo:on,380,3,5,30",
				"amp:clean,5,5,5,5,5|amp:clean,5,5,5,5,5|cab:on", "amp:clean,5,5,5,5|cab:on", "amp:clean,5,5,5,5,5|cab:maybe",
				"echo:on,380,3,5,30|amp:clean,5,5,5,5,5|cab:on", null } ) {
			Assertions.assertFalse(ChainSettings.parse(text).isPresent(), "\"" + text + "\"");
		}
		Optional<ChainSettings> clamped = ChainSettings.parse("amp:clean,50,5,5,5,5|cab:off|echo:off,99999,3,5,30");
		Assertions.assertTrue(clamped.isPresent());
		Assertions.assertEquals(10, clamped.get().amp().gain(), 1e-9);
		Assertions.assertFalse(clamped.get().amp().cabinet());
		Assertions.assertEquals(1500, clamped.get().after().get(0).value("time"), 1e-9);
		Assertions.assertFalse(clamped.get().after().get(0).enabled());
	}

	@Test
	public void equalityIsByValue() {
		ChainSettings a = ChainSettings.parse("gate:on,-60,150|amp:crunch,5,5,5,5,6|cab:on|echo:on,380,3,5,30").get();
		ChainSettings b = new ChainSettings(Arrays.asList(PedalSettings.defaults(EffectKind.GATE)),
				AmpSettings.defaults().withCharacter(AmpSettings.Character.CRUNCH).withGain(5).withBass(5).withMiddle(5).withTreble(5).withVolume(6),
				Arrays.asList(PedalSettings.defaults(EffectKind.ECHO)));
		Assertions.assertEquals(a, b);
		Assertions.assertEquals(a.hashCode(), b.hashCode());
		Assertions.assertNotEquals(a, b.withAmp(b.amp().withVolume(7)));
		Assertions.assertNotEquals(PedalSettings.defaults(EffectKind.ECHO), PedalSettings.defaults(EffectKind.ECHO).withEnabled(false));
	}
}
