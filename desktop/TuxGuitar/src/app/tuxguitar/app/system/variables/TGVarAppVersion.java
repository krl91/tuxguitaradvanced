package app.tuxguitar.app.system.variables;

import app.tuxguitar.app.system.BuildIdentity;
import app.tuxguitar.app.system.TGBuildInfo;

/** The version of the application: the release version of the build, or "dev" for a local build (spec 015). The version stamped in .tg files is TGVersion. */
public class TGVarAppVersion {

	public static final String NAME = "appversion";

	private BuildIdentity identity;

	public TGVarAppVersion() {
		this(null);
	}

	public TGVarAppVersion(BuildIdentity identity) {
		this.identity = identity;
	}

	public String toString() {
		return (this.identity != null ? this.identity : TGBuildInfo.current()).getVersion();
	}
}
