package app.tuxguitar.app.tools.custom.practice.arcade.view;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.song.models.TGString;
import app.tuxguitar.song.models.TGTrack;

/** A tuning of any number of strings for the tests, built the way the application reads it: from a track. */
final class TrackTuningFactory {

	private TrackTuningFactory() {
	}

	static TrackTuning any(int... openStrings) {
		SongFixture fixture = new SongFixture(1);
		TGTrack track = fixture.track();
		track.getStrings().clear();
		for (int i = 0; i < openStrings.length; i++) {
			TGString string = fixture.factory().newString();
			string.setNumber(i + 1);
			string.setValue(openStrings[i]);
			track.getStrings().add(string);
		}
		return TrackTuning.ofAnyStrings(track);
	}
}
