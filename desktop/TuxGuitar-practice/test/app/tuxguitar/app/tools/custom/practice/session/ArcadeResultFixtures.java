package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeResult;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeScoring;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/** A result of a whole run on the fake player, for the tests of the words of the result (other packages cannot build the fake run). */
public final class ArcadeResultFixtures {

	private ArcadeResultFixtures() {
	}

	/** Twelve notes: nine on time, one early, one wrong, one missed. */
	public static ArcadeResult some() {
		SongFixture fixture = new SongFixture(3);
		for (int i = 0; i < 12; i++) {
			fixture.quarter(i / 4, i % 4, 1, i % 5);
		}
		FakePlayerRun run = new FakePlayerRun(fixture, 100);
		run.start();
		for (int i = 0; i < 12; i++) {
			if (i == 9) {
				continue;
			}
			run.playEvent(i, i == 3 ? -150 : 0);
		}
		run.runAll();
		return new ArcadeRun(run.sequence, ArcadeScoring.standard()).result(run.last);
	}
}
