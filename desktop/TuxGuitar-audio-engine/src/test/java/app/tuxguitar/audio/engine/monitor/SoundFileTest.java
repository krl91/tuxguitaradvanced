package app.tuxguitar.audio.engine.monitor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * The sound file {@code .guitarfx} (spec 027, T041, T042, contracts/sound-file.md), on the example files GuitarFX's {@code sound_file.rs}
 * reads too. It also writes the four ready-made sounds and the every-pedal sound into {@code target/guitarfx-reference/sounds/}: the Rust
 * test reads them and writes its own, which must be byte-identical (SC-011).
 */
public class SoundFileTest {

	private static final Path EXAMPLES = Paths.get("..", "..", "guitarfx", "guitarfx-settings", "tests", "sounds");
	private static final Path SOUNDS = Paths.get("target", "guitarfx-reference", "sounds");

	private static SoundFile.Sound example(String name) throws SoundFile.ReadException {
		return SoundFile.read(EXAMPLES.resolve(name));
	}

	@Test
	public void aValidFileIsRead() throws Exception {
		SoundFile.Sound sound = example("valid.guitarfx");
		assertEquals("Lead with echo", sound.name());
		assertEquals("gate:on,-60,150|compressor:on,5,10,5|amp:highgain,7,5,6,6,5|cab:on|echo:on,380,3,5,30|reverb:on,5,5,25",
				sound.chain().toText());
	}

	@Test
	public void everyPedalAndAnEscapedNameAreRead() throws Exception {
		SoundFile.Sound sound = example("every-pedal.guitarfx");
		assertEquals("Every pedal: a = b \\ été", sound.name());
		for (EffectKind kind : EffectKind.values()) {
			assertNotNull(sound.chain().pedal(kind), kind.name());
		}
		assertFalse(sound.chain().pedal(EffectKind.COMPRESSOR).enabled());
		assertFalse(sound.chain().amp().cabinet());
	}

	@Test
	public void aNewerFormatAndAMalformedChainAreRefused() {
		assertEquals(SoundFile.Problem.NEWER, assertThrows(SoundFile.ReadException.class, () -> example("format2.guitarfx")).problem());
		assertEquals(SoundFile.Problem.NOT_A_SOUND_FILE,
				assertThrows(SoundFile.ReadException.class, () -> example("malformed.guitarfx")).problem());
		String[] texts = { "", "format=1\nname=x\n", "format=1\nchain=amp:clean,5,5,5,5,5|cab:on\n",
				"format=one\nname=x\nchain=amp:clean,5,5,5,5,5|cab:on\n", "name=x\nchain=amp:clean,5,5,5,5,5|cab:on\n",
				"format=0\nname=x\nchain=amp:clean,5,5,5,5,5|cab:on\n", "format=1\nname=   \nchain=amp:clean,5,5,5,5,5|cab:on\n" };
		for (String text : texts) {
			assertEquals(SoundFile.Problem.NOT_A_SOUND_FILE, assertThrows(SoundFile.ReadException.class, () -> SoundFile.read(text)).problem(),
					text);
		}
		assertEquals(SoundFile.Problem.NOT_A_SOUND_FILE,
				assertThrows(SoundFile.ReadException.class, () -> SoundFile.read(Paths.get("no", "such", "file.guitarfx"))).problem());
	}

	@Test
	public void namesAreTrimmedAndAtMost40Characters() throws Exception {
		String chain = "chain=amp:clean,5,5,5,5,5|cab:on\n";
		assertEquals("Mine", SoundFile.read("format=1\nname=  Mine  \n" + chain).name());
		assertNotNull(SoundFile.read("format=1\nname=" + repeat("é", 40) + "\n" + chain));
		assertThrows(SoundFile.ReadException.class, () -> SoundFile.read("format=1\nname=" + repeat("x", 41) + "\n" + chain));
	}

	@Test
	public void theWritingOrderIsCommentFormatNameChain() throws Exception {
		Path folder = Files.createTempDirectory("guitarfx-sound-file");
		Path file = folder.resolve("Every pedal.guitarfx");
		SoundFile.Sound read = example("every-pedal.guitarfx");
		SoundFile.write(file, read.name(), read.chain());
		assertArrayEquals(Files.readAllBytes(EXAMPLES.resolve("every-pedal.guitarfx")), Files.readAllBytes(file));
		try (java.util.stream.Stream<Path> files = Files.list(folder)) {
			assertEquals(1, files.count(), "no temporary file left");
		}
		SoundFile.Sound valid = example("valid.guitarfx");
		assertEquals(new String(Files.readAllBytes(EXAMPLES.resolve("valid.guitarfx")), StandardCharsets.UTF_8),
				SoundFile.text(valid.name(), valid.chain()));
	}

	/** The five sounds of SC-011, for the Rust test to read and to write again (Java → Rust, and Rust → Java by identical bytes). */
	@Test
	public void writesTheRoundTripSounds() throws Exception {
		Files.createDirectories(SOUNDS);
		for (ReadyMadeChains.Named named : ReadyMadeChains.all()) {
			String file = named.name().toLowerCase(java.util.Locale.ROOT).replace(' ', '-') + ".guitarfx";
			SoundFile.write(SOUNDS.resolve(file), named.name(), named.chain());
			SoundFile.Sound back = SoundFile.read(SOUNDS.resolve(file));
			assertEquals(named.name(), back.name());
			assertEquals(named.chain(), back.chain());
		}
		SoundFile.Sound every = example("every-pedal.guitarfx");
		SoundFile.write(SOUNDS.resolve("every-pedal.guitarfx"), every.name(), every.chain());
		assertTrue(Files.isRegularFile(SOUNDS.resolve("every-pedal.guitarfx")));
	}

	private static String repeat(String text, int count) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < count; i++) {
			builder.append(text);
		}
		return builder.toString();
	}
}
