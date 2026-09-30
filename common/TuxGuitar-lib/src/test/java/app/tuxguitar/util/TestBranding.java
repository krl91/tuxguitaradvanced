package app.tuxguitar.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class TestBranding {

	@Test
	public void replacesTheStandaloneName() {
		assertEquals("Exit TuxGuitarAdvanced", TGBranding.apply("Exit TuxGuitar"));
		assertEquals("TuxGuitarAdvanced is free.", TGBranding.apply("TuxGuitar is free."));
		assertEquals("a TuxGuitarAdvanced, b TuxGuitarAdvanced.", TGBranding.apply("a TuxGuitar, b TuxGuitar."));
		assertEquals("# TuxGuitarAdvanced", TGBranding.apply("# TuxGuitar"));
	}

	@Test
	public void replacesTheNameInCompoundsAndAfterPunctuation() {
		assertEquals("TuxGuitarAdvanced-Homepage", TGBranding.apply("TuxGuitar-Homepage"));
		assertEquals("名稱:TuxGuitarAdvanced", TGBranding.apply("名稱:TuxGuitar"));
		assertEquals("(TuxGuitarAdvanced)", TGBranding.apply("(TuxGuitar)"));
	}

	@Test
	public void keepsAttributionAndIdentifiers() {
		assertEquals("a fork of TuxGuitar (https://github.com/helge17/tuxguitar)",
				TGBranding.apply("a fork of TuxGuitar (https://github.com/helge17/tuxguitar)"));
		assertEquals("based on upstream TuxGuitar", TGBranding.apply("based on upstream TuxGuitar"));
		assertEquals("TuxGuitarAdvanced", TGBranding.apply("TuxGuitarAdvanced"));
		assertEquals("see www.TuxGuitar.app and TuxGuitar-1.6", TGBranding.apply("see www.TuxGuitar.app and TuxGuitar-1.6"));
		assertEquals("file TuxGuitarDoc.tg", TGBranding.apply("file TuxGuitarDoc.tg"));
	}

	@Test
	public void leavesOtherTextAndNullAlone() {
		assertEquals("no brand here", TGBranding.apply("no brand here"));
		assertEquals("", TGBranding.apply(""));
		assertNull(TGBranding.apply(null));
	}
}
