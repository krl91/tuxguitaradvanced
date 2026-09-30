package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/** The transport-bar Practice button's icon (in every skin) and texts (English and French) exist and are usable. */
public class PracticeToolbarAssetsTest {

	private static final String[] SKINS = { "Breeze", "Breeze-dark", "Oxygen", "Oxygen-dark", "Symbolic", "Symbolic-dark" };

	@Test
	public void everySkinHasA22PixelIconWithVisibleContentThatMatchesTheTransportIcons() throws IOException {
		for( String skin : SKINS ) {
			File icon = new File("share/skins/" + skin + "/practice_toolbar.png");
			assertTrue(icon.isFile(), "missing icon for " + skin);
			BufferedImage image = ImageIO.read(icon);
			assertNotNull(image, skin);
			BufferedImage reference = ImageIO.read(new File("../TuxGuitar/share/skins/" + skin + "/transport_icon_play.png"));
			assertEquals(reference.getWidth(), image.getWidth(), skin + " width");
			assertEquals(reference.getHeight(), image.getHeight(), skin + " height");
			int opaque = 0;
			for( int y = 0 ; y < image.getHeight() ; y ++ ) {
				for( int x = 0 ; x < image.getWidth() ; x ++ ) {
					if( (image.getRGB(x, y) >>> 24) > 128 ) {
						opaque ++;
					}
				}
			}
			assertTrue(opaque > 30 && opaque < image.getWidth() * image.getHeight() / 2, skin + " opaque pixels: " + opaque);
		}
	}

	@Test
	public void darkSkinsGetALightIconAndLightSkinsADarkOne() throws IOException {
		for( String skin : SKINS ) {
			BufferedImage image = ImageIO.read(new File("share/skins/" + skin + "/practice_toolbar.png"));
			long sum = 0;
			int count = 0;
			for( int y = 0 ; y < image.getHeight() ; y ++ ) {
				for( int x = 0 ; x < image.getWidth() ; x ++ ) {
					int argb = image.getRGB(x, y);
					if( (argb >>> 24) > 128 ) {
						sum += ((argb >> 16) & 0xff) + ((argb >> 8) & 0xff) + (argb & 0xff);
						count ++;
					}
				}
			}
			double brightness = sum / (3.0 * count);
			if( skin.endsWith("-dark") ) {
				assertTrue(brightness > 180, skin + " brightness " + brightness);
			} else {
				assertTrue(brightness < 90, skin + " brightness " + brightness);
			}
		}
	}

	@Test
	public void theButtonTextsExistInEnglishAndFrench() throws IOException {
		for( String file : new String[] { "share/lang/messages.properties", "share/lang/messages_fr.properties" } ) {
			Properties messages = new Properties();
			try( Reader reader = new InputStreamReader(Files.newInputStream(Paths.get(file)), StandardCharsets.UTF_8) ) {
				messages.load(reader);
			}
			for( String key : new String[] { "transport.practice", "transport.practice.no-song", "transport.practice.no-track" } ) {
				String value = messages.getProperty(key);
				assertNotNull(value, file + " " + key);
				assertTrue(!value.trim().isEmpty(), file + " " + key);
			}
		}
	}
}
