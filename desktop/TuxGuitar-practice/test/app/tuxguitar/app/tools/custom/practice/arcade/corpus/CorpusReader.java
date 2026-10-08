package app.tuxguitar.app.tools.custom.practice.arcade.corpus;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import app.tuxguitar.io.base.TGFileFormat;
import app.tuxguitar.io.base.TGSongReaderHandle;
import app.tuxguitar.io.base.TGSongReaderHelper;
import app.tuxguitar.io.base.TGSongStreamContext;
import app.tuxguitar.io.gtp.GP3InputStreamPlugin;
import app.tuxguitar.io.gtp.GP4InputStreamPlugin;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.util.TGContext;

/**
 * Reads a Guitar Pro file the way the application does (spec 003 FR-064, T060): the readers are registered as the plugins register them, and the format is
 * <em>detected from the content</em> by the detectors of the application; the extension is never given as a hint, so a file with the wrong extension is
 * still read as what it is, and the format reported is the one the content showed.
 */
public final class CorpusReader {

	/** What came of reading one file. */
	public static final class Result {
		public final TGSong song;
		/** The code of the format the content showed (for example "gp3"), or null. */
		public final String format;
		/** Why the file could not be read, or null. */
		public final String failure;

		Result(TGSong song, String format, String failure) {
			this.song = song;
			this.format = format;
			this.failure = failure;
		}
	}

	private final TGContext context = new TGContext();

	public CorpusReader() {
		try {
			new GP3InputStreamPlugin().connect(this.context);
			new GP4InputStreamPlugin().connect(this.context);
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	public Result read(Path file) {
		try {
			return read(Files.readAllBytes(file));
		} catch (Throwable throwable) {
			return new Result(null, null, String.valueOf(throwable));
		}
	}

	public Result read(byte[] bytes) {
		try {
			TGSongReaderHandle handle = new TGSongReaderHandle();
			handle.setContext(new TGSongStreamContext());
			handle.setFactory(new TGFactory());
			handle.setInputStream(new ByteArrayInputStream(bytes));
			new TGSongReaderHelper(this.context).read(handle);
			TGFileFormat format = handle.getFormat();
			return new Result(handle.getSong(), format != null && format.getSupportedFormats().length > 0 ? format.getSupportedFormats()[0] : null, null);
		} catch (Throwable throwable) {
			return new Result(null, null, String.valueOf(throwable));
		}
	}
}
