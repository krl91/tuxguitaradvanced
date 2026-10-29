package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.HashSet;
import java.util.Set;

import app.tuxguitar.app.tools.custom.practice.session.AudioRoute;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * The routes for which the learner chose, at the start of a run, to go on without a rhythm calibration (spec 016 FR-004): not asked again for them until
 * TuxGuitar starts again. In memory only, one set per application context; a null route is never declined.
 */
final class DeclinedRoutes {

	private final Set<AudioRoute> routes = new HashSet<AudioRoute>();

	DeclinedRoutes() {
	}

	static DeclinedRoutes of(TGContext context) {
		return TGSingletonUtil.getInstance(context, DeclinedRoutes.class.getName(), new TGSingletonFactory<DeclinedRoutes>() {
			public DeclinedRoutes createInstance(TGContext singletonContext) {
				return new DeclinedRoutes();
			}
		});
	}

	synchronized void decline(AudioRoute route) {
		if (route != null) {
			this.routes.add(route);
		}
	}

	synchronized boolean isDeclined(AudioRoute route) {
		return route != null && this.routes.contains(route);
	}
}
