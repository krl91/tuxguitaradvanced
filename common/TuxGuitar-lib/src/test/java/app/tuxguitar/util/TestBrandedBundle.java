package app.tuxguitar.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import app.tuxguitar.resource.TGResourceBundle;

public class TestBrandedBundle {

	@Test
	public void mapsEveryMessageAndKeepsTheLocale() {
		Properties properties = new Properties();
		properties.setProperty("action.file.exit", "Exit TuxGuitar");
		properties.setProperty("close", "Close");
		TGResourceBundle bundle = new TGResourceBundle(Locale.FRENCH, properties);

		TGResourceBundle branded = bundle.mapValues(new TGResourceBundle.ValueMapper() {
			public String map(String value) {
				return TGBranding.apply(value);
			}
		});

		assertEquals("Exit TuxGuitarAdvanced", branded.getString("action.file.exit"));
		assertEquals("Close", branded.getString("close"));
		assertEquals(Locale.FRENCH, branded.getLocale());
		// the original is untouched
		assertEquals("Exit TuxGuitar", bundle.getString("action.file.exit"));
	}
}
