package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;

/** The two buttons of the transport bar and the mode each one starts directly (owner, 2026-10-15): the note button Wait for the right note, the coloured one Arcade. */
enum TransportButton {

	PRACTICE(FollowSettings.Mode.WAIT),
	ARCADE(FollowSettings.Mode.ARCADE);

	private final FollowSettings.Mode mode;

	TransportButton(FollowSettings.Mode mode) {
		this.mode = mode;
	}

	FollowSettings.Mode mode() {
		return this.mode;
	}
}
