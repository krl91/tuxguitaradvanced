package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIFont;
import app.tuxguitar.ui.resource.UIPainter;

/**
 * The baseline renderer replays the scene on the toolkit's painter (spec 003 T041): one drawing for each item and nothing more, the colours and fonts
 * made once and released, the alpha put back. The painter and the factory are recording stand-ins, so this runs without a window; the native test
 * (T043) runs the same replay on the real SWT painter.
 */
public class UiPainterRendererTest {

	private static final Viewport SCREEN = new Viewport(1280, 720);

	/** Records every call; returns 0 or null, and 10 for the metrics of a text. */
	private static final class Recording implements InvocationHandler {
		final List<String> calls = new ArrayList<String>();
		int createdColours;
		int createdFonts;
		int disposed;

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			this.calls.add(method.getName() + (args == null ? "" : java.util.Arrays.toString(args)));
			switch (method.getName()) {
				case "getFMWidth":
				case "getFMHeight":
					return 10f;
				case "createColor":
					this.createdColours++;
					return resource(UIColor.class);
				case "createFont":
					this.createdFonts++;
					return resource(UIFont.class);
				case "isDisposed":
					return false;
				case "dispose":
					this.disposed++;
					return null;
				default:
					return method.getReturnType() == float.class ? (Object) 0f : method.getReturnType() == int.class ? (Object) 0 : method.getReturnType() == boolean.class ? (Object) false : null;
			}
		}

		private Object resource(Class<?> type) {
			return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (p, m, a) -> {
				if (m.getName().equals("dispose")) {
					this.disposed++;
				}
				return m.getReturnType() == boolean.class ? (Object) false : m.getReturnType() == int.class ? (Object) 0 : m.getReturnType() == float.class ? (Object) 0f : null;
			});
		}

		int count(String name) {
			int n = 0;
			for (String call : this.calls) {
				if (call.startsWith(name + "[")) {
					n++;
				}
			}
			return n;
		}
	}

	private static Object proxy(Class<?> type, InvocationHandler handler) {
		return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private static Scene scene() {
		SongFixture song = new SongFixture(1);
		song.quarter(0, 0, 6, 3);
		song.chord(0, 1, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		return fixture.scene(false).build(fixture.onset(0) - 300 * SceneFixture.MS, SCREEN, null, 100);
	}

	@Test
	public void everyVisibleItemIsDrawnOnceWithItsKindOfPath() {
		Scene scene = scene();
		Recording painterCalls = new Recording();
		Recording factoryCalls = new Recording();
		UiPainterRenderer renderer = new UiPainterRenderer((UIFactory) proxy(UIFactory.class, factoryCalls), "Sans");
		renderer.target((UIPainter) proxy(UIPainter.class, painterCalls));
		renderer.render(scene, QualityTier.FULL);

		int texts = 0;
		int fills = 0;
		int draws = 0;
		for (SceneItem item : scene.items()) {
			switch (item.shape()) {
				case TEXT:
					texts++;
					break;
				case QUAD:
				case DISC:
					fills++;
					break;
				default:
					draws++;
			}
		}
		assertEquals(texts, painterCalls.count("drawString"));
		assertEquals(fills, painterCalls.calls.stream().filter(c -> c.equals("initPath[" + UIPainter.PATH_FILL + "]")).count());
		assertEquals(draws, painterCalls.calls.stream().filter(c -> c.equals("initPath[" + UIPainter.PATH_DRAW + "]")).count());
		assertEquals(scene.items().size() - texts, painterCalls.count("initPath"), "one path for each shape, none for a text");
		assertEquals("setAlpha[255]", painterCalls.calls.get(painterCalls.calls.size() - 1), "the alpha is put back");
		Set<Integer> colours = new HashSet<Integer>();
		Set<Integer> sizes = new HashSet<Integer>();
		for (SceneItem item : scene.items()) {
			colours.add(item.colour());
			if (item.shape() == SceneItem.Shape.TEXT) {
				sizes.add(Math.max(6, (int) Math.round(item.value(2))));
			}
		}
		assertEquals(colours.size(), factoryCalls.createdColours, "a colour is made once, however many items use it");
		assertEquals(sizes.size(), factoryCalls.createdFonts, "a font size is made once");
		renderer.render(scene, QualityTier.FULL);
		assertEquals(colours.size(), factoryCalls.createdColours, "and kept for the next frame");
	}

	@Test
	public void theCheapestTierSwitchesTheAntialiasingOffForTheFieldButNotForTheText() {
		Scene scene = scene();
		Recording painterCalls = new Recording();
		UiPainterRenderer renderer = new UiPainterRenderer((UIFactory) proxy(UIFactory.class, new Recording()), null);
		renderer.target((UIPainter) proxy(UIPainter.class, painterCalls));
		renderer.render(scene, QualityTier.PERFORMANCE);
		boolean textSeenAfterField = false;
		String lastAntialias = null;
		for (String call : painterCalls.calls) {
			if (call.startsWith("setAntialias")) {
				lastAntialias = call;
			}
			if (call.startsWith("drawString")) {
				assertEquals("setAntialias[true]", lastAntialias, "text stays smooth");
				textSeenAfterField = true;
			}
			if (call.startsWith("initPath")) {
				assertEquals("setAntialias[false]", lastAntialias, "the field is drawn without antialiasing at the cheapest tier");
			}
		}
		assertTrue(textSeenAfterField);
	}

	@Test
	public void disposeReleasesEveryColourAndFontThatWasMade() {
		Scene scene = scene();
		Recording painterCalls = new Recording();
		Recording factoryCalls = new Recording();
		UiPainterRenderer renderer = new UiPainterRenderer((UIFactory) proxy(UIFactory.class, factoryCalls), "Sans");
		renderer.target((UIPainter) proxy(UIPainter.class, painterCalls));
		renderer.render(scene, QualityTier.BALANCED);
		int resources = factoryCalls.createdColours + factoryCalls.createdFonts;
		assertTrue(resources > 0);
		renderer.dispose();
		assertEquals(resources, factoryCalls.disposed, "every colour and font made is disposed");
		assertFalse(painterCalls.calls.isEmpty());
	}
}
