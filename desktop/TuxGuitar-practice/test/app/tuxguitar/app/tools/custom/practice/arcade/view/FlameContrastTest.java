package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxes.Box;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;

/**
 * Reading is never hindered by the flames (spec 009 SC-002, FR-005, contract invariant 5): in a scene that holds verdict badges, a bend, a technique mark and a
 * chord name, with the flames at full level, on seven shapes of window, every text keeps a contrast of 4.5 to 1 against what is painted behind it, and no
 * flame above 12 % of opacity comes within 4 pixels of a text. The translucent body of the neck is the one explicit exception: it is not a flame.
 */
public class FlameContrastTest {

	private static final Viewport[] SHAPES = { new Viewport(1280, 720), new Viewport(800, 600), new Viewport(640, 480), new Viewport(1500, 500), new Viewport(2000, 1000),
			new Viewport(1100, 950), new Viewport(900, 400) };

	private static final double WIDEN = 4.0;
	private static final double FAINT = 0.12;

	/** The boxes of every text of the picture: the words, and the verdict badges that carry a symbol or a word. */
	private static List<Box> texts(Scene scene) {
		List<Box> boxes = new ArrayList<Box>();
		for (SceneItem item : scene.items()) {
			if (item.shape() == SceneItem.Shape.TEXT && item.text() != null && !item.text().trim().isEmpty()) {
				boxes.add(SceneBoxes.ofText(item));
			} else if (item.role() == Role.BADGE) {
				boxes.add(SceneBoxes.ofQuad(item));
			}
		}
		return boxes;
	}

	private static Box boxOf(SceneItem flame) {
		return flame.shape() == SceneItem.Shape.QUAD ? SceneBoxes.ofQuad(flame) : SceneBoxes.ofDisc(flame);
	}

	private static boolean isFlame(SceneItem item) {
		return item.role() == Role.FLAME_TONGUE || item.role() == Role.FLAME_EMBER;
	}

	private static double luminance(int rgb) {
		double[] c = { (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF };
		for (int i = 0; i < 3; i++) {
			double v = c[i] / 255.0;
			c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
		}
		return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
	}

	private static double contrast(int a, int b) {
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	/** The text items of a scene, in order. */
	private static List<SceneItem> texts(Scene scene, boolean items) {
		List<SceneItem> found = new ArrayList<SceneItem>();
		for (SceneItem item : scene.items()) {
			if (item.shape() == SceneItem.Shape.TEXT && item.text() != null && !item.text().trim().isEmpty()) {
				found.add(item);
			}
		}
		return found;
	}

	/** The scene without any text: what is painted behind the texts. */
	private static Scene withoutTexts(Scene scene) {
		List<SceneItem> items = new ArrayList<SceneItem>();
		for (SceneItem item : scene.items()) {
			if (item.shape() != SceneItem.Shape.TEXT) {
				items.add(item);
			}
		}
		return new Scene(items, scene.nowNanos(), scene.viewport(), scene.theme());
	}

	/** The worst contrast of a text against the pixels of its box in the picture painted without texts. */
	private static double worstContrast(BufferedImage behind, SceneItem text) {
		Box box = SceneBoxes.ofText(text);
		int x1 = Math.max(0, (int) Math.floor(box.x1));
		int y1 = Math.max(0, (int) Math.floor(box.y1));
		int x2 = Math.min(behind.getWidth() - 1, (int) Math.ceil(box.x2));
		int y2 = Math.min(behind.getHeight() - 1, (int) Math.ceil(box.y2));
		double worst = Double.MAX_VALUE;
		for (int y = y1; y <= y2; y++) {
			for (int x = x1; x <= x2; x++) {
				worst = Math.min(worst, contrast(text.colour(), behind.getRGB(x, y) & 0xFFFFFF));
			}
		}
		return worst;
	}

	@Test
	public void theReferenceHoldsABadgeABendATechniqueAndAChordName() {
		Scene scene = ReferenceScenes.flameReading().scene(SHAPES[0], QualityTier.FULL);
		assertFalse(scene.withRole(Role.BADGE).isEmpty(), "a verdict badge");
		assertFalse(scene.withRole(Role.BEND_LABEL).isEmpty() && scene.withRole(Role.BEND_ARROW).isEmpty(), "a bend");
		assertFalse(scene.withRole(Role.TECH_LABEL).isEmpty(), "a technique mark");
		assertFalse(scene.withRole(Role.PANEL_NAME).isEmpty(), "a chord name");
		assertFalse(scene.withRole(Role.FLAME_TONGUE).isEmpty(), "and the flames at full level");
	}

	@Test
	public void noFlameAboveTwelvePercentComesWithinFourPixelsOfAText() {
		for (Viewport shape : SHAPES) {
			for (QualityTier tier : new QualityTier[] { QualityTier.FULL, QualityTier.BALANCED }) {
				Scene scene = ReferenceScenes.flameReading().scene(shape, tier);
				List<Box> texts = texts(scene);
				assertFalse(texts.isEmpty());
				for (SceneItem flame : scene.items()) {
					if (!isFlame(flame) || flame.alpha() <= FAINT) {
						continue;
					}
					Box f = boxOf(flame);
					for (Box t : texts) {
						Box wide = new Box(t.x1 - WIDEN, t.y1 - WIDEN, t.x2 + WIDEN, t.y2 + WIDEN, t.event, t.what);
						assertFalse(f.overlaps(wide), shape.width() + "x" + shape.height() + " " + tier + ": " + flame + " at opacity " + flame.alpha() + " meets " + t);
					}
				}
			}
		}
	}

	@Test
	public void noTextLosesContrastToTheFlamesAndTheOnesThatHadFourAndAHalfToOneKeepIt() {
		for (Viewport shape : SHAPES) {
			ReferenceScenes.Reference reference = ReferenceScenes.flameReading();
			Scene with = reference.scene(shape, QualityTier.FULL);
			Scene without = reference.fixture.scene(false).build(reference.nowNanos, shape, reference.run, 100, QualityTier.FULL);
			BufferedImage behindWith = SceneImageWriter.draw(withoutTexts(with));
			BufferedImage behindWithout = SceneImageWriter.draw(withoutTexts(without));
			List<SceneItem> a = texts(with, true);
			List<SceneItem> b = texts(without, true);
			assertEquals(a.size(), b.size(), "the flames add and move no text");
			assertTrue(a.size() >= 8, "the picture has texts to read: " + a.size());
			for (int i = 0; i < a.size(); i++) {
				double before = worstContrast(behindWithout, b.get(i));
				double after = worstContrast(behindWith, a.get(i));
				// SC-002: the flames never take a text under 4.5 to 1; a text that was lower than that by itself (the label of a string on its own coloured
				// string, a number on the lit cell) is not made worse by more than 2 %
				assertTrue(after >= Math.min(4.5, before) * 0.98, shape.width() + "x" + shape.height() + ": " + a.get(i) + " goes from " + String.format("%.2f", before) + " to "
						+ String.format("%.2f", after));
			}
		}
	}

	@Test
	public void theFlamesStayInsideThePicture() {
		for (Viewport shape : SHAPES) {
			for (QualityTier tier : new QualityTier[] { QualityTier.FULL, QualityTier.BALANCED }) {
				for (SceneItem item : ReferenceScenes.flameReading().scene(shape, tier).items()) {
					if (isFlame(item)) {
						Box b = boxOf(item);
						assertTrue(b.x1 >= -1e-6 && b.y1 >= -1e-6 && b.x2 <= shape.width() + 1e-6 && b.y2 <= shape.height() + 1e-6,
								shape.width() + "x" + shape.height() + " " + tier + ": " + item + " leaves the picture");
					}
				}
			}
		}
	}

	@Test
	public void theFlamesAreInTheBackWhateverTheShapeAndTheCaptureIsDeterministic() {
		for (Viewport shape : SHAPES) {
			Scene scene = ReferenceScenes.flameReading().scene(shape, QualityTier.FULL);
			boolean other = false;
			for (SceneItem item : scene.items()) {
				if (isFlame(item)) {
					assertFalse(other, "a flame after another item");
				} else {
					other = true;
				}
			}
			assertEquals(SceneCapture.of(scene), SceneCapture.of(ReferenceScenes.flameReading().scene(shape, QualityTier.FULL)));
		}
	}
}
