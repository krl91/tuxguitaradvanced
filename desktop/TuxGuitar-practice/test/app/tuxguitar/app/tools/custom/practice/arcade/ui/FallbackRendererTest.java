package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ReferenceScenes;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIFont;
import app.tuxguitar.ui.resource.UIPainter;

/**
 * The fallback path of the canvas (spec 003 FR-057, SC-016, T046, T048): forced, it paints the same information at the same places with the same
 * texts as the selected path; when the selected path fails, the frame is painted again by the fallback and the fallback stays; nothing of the scene
 * depends on the choice.
 */
public class FallbackRendererTest {

	/** A painter that records its calls, and can refuse a transparency, as a painter without that capability would. */
	private static final class Recording implements InvocationHandler {
		final List<String> calls = new ArrayList<String>();
		final List<String> texts = new ArrayList<String>();
		boolean refuseAlpha;

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			String name = method.getName();
			if (name.equals("setAlpha") && this.refuseAlpha && (Integer) args[0] != 255) {
				throw new UnsupportedOperationException("no transparency");
			}
			this.calls.add(name + (args == null ? "" : java.util.Arrays.toString(args)));
			if (name.equals("drawString")) {
				this.texts.add(args[0] + "@" + Math.round((Float) args[1]) + "," + Math.round((Float) args[2]));
			}
			switch (name) {
				case "getFMWidth":
				case "getFMHeight":
					return 10f;
				default:
					return method.getReturnType() == float.class ? (Object) 0f : null;
			}
		}

		int count(String prefix) {
			int n = 0;
			for (String call : this.calls) {
				if (call.startsWith(prefix)) {
					n++;
				}
			}
			return n;
		}
	}

	private static UIFactory factory() {
		return (UIFactory) Proxy.newProxyInstance(UIFactory.class.getClassLoader(), new Class<?>[] { UIFactory.class }, (p, method, args) -> {
			if (method.getName().equals("createColor")) {
				return Proxy.newProxyInstance(UIColor.class.getClassLoader(), new Class<?>[] { UIColor.class }, (q, m, a) -> null);
			}
			if (method.getName().equals("createFont")) {
				return Proxy.newProxyInstance(UIFont.class.getClassLoader(), new Class<?>[] { UIFont.class }, (q, m, a) -> null);
			}
			return null;
		});
	}

	private static UIPainter painter(Recording recording) {
		return (UIPainter) Proxy.newProxyInstance(UIPainter.class.getClassLoader(), new Class<?>[] { UIPainter.class }, recording);
	}

	@Test
	public void theFallbackDrawsTheSameInformationAtTheSamePlacesWithTheSameTextsAndNothingElse() {
		for (ReferenceScenes.Reference reference : ReferenceScenes.all()) {
			Scene scene = reference.scene(false, QualityTier.FULL);
			Recording selectedCalls = new Recording();
			UiPainterRenderer selected = new UiPainterRenderer(factory(), "Sans");
			selected.target(painter(selectedCalls));
			selected.render(scene, QualityTier.FULL);
			Recording fallbackCalls = new Recording();
			UiPainterRenderer fallback = UiPainterRenderer.fallback(factory(), "Sans");
			fallback.target(painter(fallbackCalls));
			fallback.render(scene, QualityTier.FULL);

			assertEquals(selectedCalls.texts, fallbackCalls.texts, reference.name + ": the same texts at the same places");
			int shapes = 0;
			for (SceneItem item : scene.items()) {
				boolean left = item.role().isDecoration() || item.role() == SceneItem.Role.FIELD_GUIDE || item.role() == SceneItem.Role.INLAY;
				if (item.shape() != SceneItem.Shape.TEXT && !left) {
					shapes++;
				}
			}
			assertEquals(shapes, fallbackCalls.count("initPath"), reference.name + ": one path for each shape of the information");
			assertTrue(selectedCalls.count("initPath") > fallbackCalls.count("initPath"), "the selected path draws the depth cues and the decoration too");
			assertEquals(fallbackCalls.count("setAlpha"), fallbackCalls.calls.stream().filter(c -> c.equals("setAlpha[255]")).count(), "the fallback never blends");
			assertTrue(selectedCalls.calls.stream().anyMatch(c -> c.startsWith("setAlpha") && !c.equals("setAlpha[255]")), "while the selected path does");
		}
	}

	@Test
	public void aForcedFallbackNeverUsesTheSelectedPath() {
		Scene scene = ReferenceScenes.sixStrings().scene(false, QualityTier.FULL);
		Recording recording = new Recording();
		recording.refuseAlpha = true;
		GuardedRenderer guarded = new GuardedRenderer(new UiPainterRenderer(factory(), "Sans"), UiPainterRenderer.fallback(factory(), "Sans"), true);
		guarded.target(painter(recording));
		guarded.render(scene, QualityTier.FULL);
		assertTrue(guarded.isUsingFallback());
		assertEquals(null, guarded.failure(), "forced, not failed");
		assertTrue(guarded.name().contains("fallback"));
		assertEquals(recording.count("setAlpha"), recording.calls.stream().filter(c -> c.equals("setAlpha[255]")).count(), "only the opaque alpha: no transparency was asked");
	}

	@Test
	public void whenTheSelectedPathCannotPaintTheFrameIsPaintedByTheFallbackAndTheFallbackStays() {
		Scene scene = ReferenceScenes.denseRealistic().scene(false, QualityTier.FULL);
		Recording recording = new Recording();
		recording.refuseAlpha = true;
		GuardedRenderer guarded = new GuardedRenderer(new UiPainterRenderer(factory(), "Sans"), UiPainterRenderer.fallback(factory(), "Sans"), false);
		guarded.target(painter(recording));
		assertFalse(guarded.isUsingFallback());
		guarded.render(scene, QualityTier.FULL);
		assertTrue(guarded.isUsingFallback(), "the selected path failed on its first transparency");
		assertNotNull(guarded.failure());
		assertTrue(guarded.failure().contains("no transparency"));
		int texts = recording.texts.size();
		assertTrue(texts > 0, "and the frame was painted all the same");
		guarded.render(scene, QualityTier.FULL);
		assertEquals(2 * texts, recording.texts.size(), "the next frame goes straight to the fallback");
	}

	@Test
	public void theSceneIsTheSameWhicheverPathPaintsIt() {
		ReferenceScenes.Reference reference = ReferenceScenes.denseRealistic();
		String before = app.tuxguitar.app.tools.custom.practice.arcade.view.SceneCapture.of(reference.scene(false, QualityTier.FULL));
		GuardedRenderer guarded = new GuardedRenderer(new UiPainterRenderer(factory(), "Sans"), UiPainterRenderer.fallback(factory(), "Sans"), true);
		Recording recording = new Recording();
		guarded.target(painter(recording));
		guarded.render(reference.scene(false, QualityTier.FULL), QualityTier.FULL);
		assertEquals(before, app.tuxguitar.app.tools.custom.practice.arcade.view.SceneCapture.of(reference.scene(false, QualityTier.FULL)));
	}
}
