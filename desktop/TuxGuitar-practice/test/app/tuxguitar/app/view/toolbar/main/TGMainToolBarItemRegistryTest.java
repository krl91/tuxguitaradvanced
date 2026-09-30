package app.tuxguitar.app.view.toolbar.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import app.tuxguitar.util.TGContext;

/** Registry and default-layout behaviour of the tool bar extension point (spec 002, contracts/toolbar-extension.md). */
public class TGMainToolBarItemRegistryTest {

	private static TGMainToolBarPluginItem item(String name, String after) {
		return new TGMainToolBarPluginItem(name, "transport", "practice.toggle", "practice_toolbar.png", true, null, after);
	}

	@Test
	public void registeringReplacesByNameAndUnregisteringUnknownIsHarmless() {
		TGMainToolBarItemRegistry registry = new TGMainToolBarItemRegistry();
		registry.register(item("transport.practice", "transport.start"));
		registry.register(item("transport.practice", "transport.stop"));
		assertEquals(1, registry.items().size());
		assertEquals("transport.stop", registry.items().get(0).getDefaultAfter());

		registry.unregister("unknown");
		assertEquals(1, registry.items().size());
		registry.unregister("transport.practice");
		assertTrue(registry.items().isEmpty());
		registry.unregister("transport.practice");
		assertTrue(registry.items().isEmpty());
	}

	@Test
	public void listenersAreToldOnlyWhenSomethingChanges() {
		TGMainToolBarItemRegistry registry = new TGMainToolBarItemRegistry();
		AtomicInteger told = new AtomicInteger();
		Runnable listener = told::incrementAndGet;
		registry.addChangeListener(listener);
		registry.register(item("a", null));
		registry.unregister("missing");
		registry.unregister("a");
		assertEquals(2, told.get());
		registry.removeChangeListener(listener);
		registry.register(item("b", null));
		assertEquals(2, told.get());
		// a failing listener does not stop the others
		registry.addChangeListener(() -> { throw new IllegalStateException("broken"); });
		registry.addChangeListener(told::incrementAndGet);
		registry.unregister("b");
		assertEquals(3, told.get());
	}

	@Test
	public void theSameRegistryServesOneContext() {
		TGContext context = new TGContext();
		assertSame(TGMainToolBarItemRegistry.getInstance(context), TGMainToolBarItemRegistry.getInstance(context));
		assertFalse(TGMainToolBarItemRegistry.getInstance(context) == TGMainToolBarItemRegistry.getInstance(new TGContext()));
	}

	@Test
	public void anItemNeedsANameAndAnAction() {
		assertThrows(IllegalArgumentException.class, () -> new TGMainToolBarPluginItem("", "g", "action", "icon.png", false, null, null));
		assertThrows(IllegalArgumentException.class, () -> new TGMainToolBarPluginItem("name", "g", null, "icon.png", false, null, null));
	}

	@Test
	public void theDefaultLayoutIsUnchangedWithoutRegisteredItems() {
		List<String> centre = new TGMainToolBarConfig("").getAreaContent(TGMainToolBar.CENTER_AREA);
		TGMainToolBarItemRegistry registry = new TGMainToolBarItemRegistry();
		assertEquals(centre, registry.withDefaultItems(centre));
		assertTrue(centre.contains("transport.start"));
	}

	@Test
	public void aRegisteredItemAppearsRightAfterItsAnchorInTheDefaultCentreArea() {
		List<String> centre = new TGMainToolBarConfig("").getAreaContent(TGMainToolBar.CENTER_AREA);
		List<String> original = new ArrayList<String>(centre);
		TGMainToolBarItemRegistry registry = new TGMainToolBarItemRegistry();
		registry.register(item("transport.practice", "transport.start"));

		List<String> result = registry.withDefaultItems(centre);

		int start = result.indexOf("transport.start");
		assertEquals("transport.practice", result.get(start + 1));
		assertEquals(original.size() + 1, result.size());
		// the list of the default configuration itself is not modified
		assertEquals(original, centre);
		// not added twice, and other areas that do not hold the anchor are left alone
		assertEquals(result, registry.withDefaultItems(result));
		List<String> left = new TGMainToolBarConfig("").getAreaContent(TGMainToolBar.LEFT_AREA);
		assertEquals(left, registry.withDefaultItems(left));
	}

	@Test
	public void anItemWithoutAnAnchorIsOnlyOfferedInTheSettingsNotPlacedByDefault() {
		List<String> centre = new TGMainToolBarConfig("").getAreaContent(TGMainToolBar.CENTER_AREA);
		TGMainToolBarItemRegistry registry = new TGMainToolBarItemRegistry();
		registry.register(item("plugin.only", null));
		assertEquals(centre, registry.withDefaultItems(centre));
	}

	@Test
	public void theConfigMapOffersRegisteredItemsAsCheckableActionsInTheirGroup() {
		TGContext context = new TGContext();
		TGMainToolBarConfigMap without = new TGMainToolBarConfigMap(context);
		assertNull(without.getToolBarItemConfig("transport.practice"));

		TGMainToolBarItemRegistry.getInstance(context).register(item("transport.practice", "transport.start"));
		TGMainToolBarConfigMap with = new TGMainToolBarConfigMap(context);
		TGMainToolBarItemConfig config = with.getToolBarItemConfig("transport.practice");
		assertNotNull(config);
		assertEquals("practice.toggle", config.getActionName());
		assertEquals(TGMainToolBarItemConfig.CHECKABLE_ITEM, config.getType());
		assertEquals("practice_toolbar.png", config.getIconFileName());
		assertTrue(with.getToolBarItemNames().contains("transport.practice"));
		// the built-in items are all still there
		assertTrue(with.getToolBarItemNames().containsAll(Arrays.asList("transport.start", "transport.stop", "transport.metronome")));
		// another context is not affected
		assertNull(new TGMainToolBarConfigMap(new TGContext()).getToolBarItemConfig("transport.practice"));
	}

	@Test
	public void aSavedLayoutNamingAnUnregisteredItemSimplyLacksIt() {
		TGMainToolBarConfigMap map = new TGMainToolBarConfigMap(new TGContext());
		// the tool bar ignores a configured name that has no item (see TGMainToolBar.layout)
		assertNull(map.getToolBarItemConfig("transport.practice"));
	}
}
