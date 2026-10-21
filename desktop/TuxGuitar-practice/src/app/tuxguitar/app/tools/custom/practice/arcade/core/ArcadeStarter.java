package app.tuxguitar.app.tools.custom.practice.arcade.core;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import app.tuxguitar.app.tools.custom.practice.model.ArcadeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.FollowRun;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.session.FollowStarter;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.TechniqueJudge;
import app.tuxguitar.song.models.TGMeasure;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

/**
 * Prepares an Arcade run (spec 003 FR-002, FR-010, FR-011, research R8): the one track the learner performs is chosen explicitly and passed in; every other
 * playable track is the MIDI backing. The run is the tempo-following run of specification 002 ({@link FollowStarter}): the chosen track is the only one
 * muted, in the player only, and the song, its mute and solo and its undo stack are not touched. Nothing is started here: the caller starts the
 * {@link FollowRun} when the learner asks.
 */
public final class ArcadeStarter {

	private ArcadeStarter() {
	}

	/**
	 * @param track the track the learner chose to perform; never defaulted here
	 * @param caretMeasure the measure under the score cursor, or null (the run begins at the first event)
	 * @param caretPosition the cursor's position in ticks in that measure
	 * @throws IllegalArgumentException when no track was chosen or the track cannot be performed (percussion, no events, no defined strings)
	 */
	public static FollowStarter.Plan prepare(TGContext context, TGSong song, TGTrack track, TGMeasure caretMeasure, long caretPosition,
			FollowSettings settings, PracticeParameters parameters, TechniqueJudge judge, Supplier<PolyphonicAcceptance> polyphonic,
			FollowStarter.Environment environment, Consumer<FollowSession.Snapshot> uiSink, Executor uiExecutor, Consumer<FollowRun.Ended> endListener,
			FollowRun.EventsFactory eventsFactory, Runnable restart) {
		if (track == null) {
			throw new IllegalArgumentException("the learner must choose the track to perform");
		}
		ArcadeTrackCandidates candidates = ArcadeTrackCandidates.evaluate(song, null);
		boolean performable = false;
		for (app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidate candidate : candidates.getCandidates()) {
			performable |= (candidate.getTrack() == track);
		}
		if (!performable) {
			throw new IllegalArgumentException("this track cannot be performed: " + candidates.getExclusions().get(track));
		}
		PracticeSequence sequence = PracticeSequenceBuilder.build(song, track, TrackTuning.ofAnyStrings(track), caretMeasure, caretPosition);
		return prepareFrom(context, song, track, sequence, settings, parameters, judge, polyphonic, environment, uiSink, uiExecutor, endListener, eventsFactory, restart);
	}

	/**
	 * The same run from a sequence the caller made (the replay of a section starts from a chosen measure: {@code PracticeSequence.startingAt}); the checks are
	 * those of {@link #prepare}.
	 */
	public static FollowStarter.Plan prepareFrom(TGContext context, TGSong song, TGTrack track, PracticeSequence sequence, FollowSettings settings,
			PracticeParameters parameters, TechniqueJudge judge, Supplier<PolyphonicAcceptance> polyphonic, FollowStarter.Environment environment,
			Consumer<FollowSession.Snapshot> uiSink, Executor uiExecutor, Consumer<FollowRun.Ended> endListener, FollowRun.EventsFactory eventsFactory,
			Runnable restart) {
		return prepareFrom(context, song, track, sequence, settings, settings.getWindowMillis(), parameters, judge, polyphonic, environment, uiSink, uiExecutor,
				endListener, eventsFactory, restart);
	}

	/**
	 * The same run with the width of the on-time window given (spec 014: the permissiveness of Arcade widens the window of the learner, never beyond the range of
	 * spec 012); see {@link FollowStarter#prepare}.
	 */
	public static FollowStarter.Plan prepareFrom(TGContext context, TGSong song, TGTrack track, PracticeSequence sequence, FollowSettings settings, int windowMillis,
			PracticeParameters parameters, TechniqueJudge judge, Supplier<PolyphonicAcceptance> polyphonic, FollowStarter.Environment environment,
			Consumer<FollowSession.Snapshot> uiSink, Executor uiExecutor, Consumer<FollowRun.Ended> endListener, FollowRun.EventsFactory eventsFactory,
			Runnable restart) {
		if (track == null) {
			throw new IllegalArgumentException("the learner must choose the track to perform");
		}
		return FollowStarter.prepare(context, song, sequence, track.getNumber(), settings, windowMillis, parameters, judge, polyphonic, environment, uiSink, uiExecutor,
				endListener, eventsFactory, restart);
	}
}
