package app.tuxguitar.app.tools.custom.practice.session;

import java.util.List;

/**
 * Which input to select when the list of inputs is read (owner, 2026-10-11): the one the learner remembered as the default, when it is there and it is
 * not already the current one, at the first reading and when it appears again after an absence. While it was there at the previous reading, a choice of
 * the learner is kept. When it is away nothing is forced: the usual fallback (the system default) applies.
 */
public final class InputDefaultPolicy {

	private InputDefaultPolicy() {
	}

	/**
	 * @param availableIds the inputs that are there now
	 * @param currentId the current input, or null
	 * @param rememberedId the remembered default input, or null
	 * @param wasThereBefore the remembered input was in the previous reading of this list (false at the first reading)
	 * @return the id to select, or null to leave the selection as it is
	 */
	public static String choose(List<String> availableIds, String currentId, String rememberedId, boolean wasThereBefore) {
		if (rememberedId == null || !availableIds.contains(rememberedId) || rememberedId.equals(currentId)) {
			return null;
		}
		if (currentId == null || !wasThereBefore) {
			return rememberedId;
		}
		return null;
	}
}
