package app.tuxguitar.app.system;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import app.tuxguitar.app.util.TGFileUtils;

/**
 * Reads the build identity of the running application (spec 015) from {@code build-info.properties} in the share folder. It never throws: a missing or
 * unreadable file gives the development identity.
 */
public final class TGBuildInfo {

	public static final String FILE_NAME = "build-info.properties";

	private static BuildIdentity current;

	private TGBuildInfo() {
	}

	public static synchronized BuildIdentity current() {
		if (current == null) {
			current = readFirst(TGFileUtils.TG_STATIC_SHARED_PATHS, TGFileUtils.PATH_HOME);
		}
		return current;
	}

	/** Looks for the file in each share folder (a relative one is relative to the home folder) and reads the first found; the development identity if none. */
	public static BuildIdentity readFirst(String[] sharePaths, String homePath) {
		if (sharePaths != null) {
			for (String path : sharePaths) {
				if (path == null || path.length() == 0) {
					continue;
				}
				File folder = new File(path);
				if (!folder.isAbsolute() && homePath != null) {
					folder = new File(homePath, path);
				}
				if (new File(folder, FILE_NAME).isFile()) {
					return read(folder.getPath());
				}
			}
		}
		return BuildIdentity.fromProperties(null);
	}

	/** Reads the file in the given share folder (a path ending with a separator or not), or the development identity. */
	public static BuildIdentity read(String sharePath) {
		Properties properties = null;
		if (sharePath != null) {
			File file = new File(sharePath, FILE_NAME);
			if (file.isFile()) {
				try (InputStream stream = new FileInputStream(file)) {
					properties = new Properties();
					properties.load(stream);
				} catch (Throwable throwable) {
					properties = null;
				}
			}
		}
		return BuildIdentity.fromProperties(properties);
	}
}
