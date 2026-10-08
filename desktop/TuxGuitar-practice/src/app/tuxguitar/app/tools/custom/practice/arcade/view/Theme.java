package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * The look of the neutral parts of the scene (spec 003 FR-065, SC-023): the dark one, which is the design, and a light one for a light interface. The colours
 * of the strings and of the verdicts are the same in both, and the outline of the target on the neck is always the colour that stands out most from the neck.
 */
public enum Theme {
	DARK, LIGHT;

	/** The colour of a neutral part of the scene in this theme, from its colour in the dark design; any other colour is unchanged. */
	int map(int dark) {
		if (this == DARK) {
			return dark;
		}
		switch (dark) {
			case ArcadeScene.NECK_COLOUR:
				return 0xE6E9EF;
			case ArcadeScene.WIRE_COLOUR:
				return 0x59606D;
			case ArcadeScene.INLAY_COLOUR:
				return 0xB3BAC6;
			case ArcadeScene.HIT_COLOUR:
				return 0x1C2027;
			case ArcadeScene.BAR_COLOUR:
				return 0x2A303A;
			case ArcadeScene.TEXT_COLOUR:
				return 0x181B21;
			case ArcadeScene.FRAME_COLOUR:
				return 0xF7F8FA;
			case ArcadeScene.DIAGRAM_LINE:
				return 0x464E5C;
			case ArcadeScene.HEADER_COLOUR:
				return 0xD3D9E3;
			case ArcadeScene.HEADER_TEXT_COLOUR:
				return 0x10131A;
			case ArcadeScene.OUTLINE_COLOUR:
				return 0x101216;
			default:
				return dark;
		}
	}

	/** The colour of the background behind the scene, for the images. */
	public int background() {
		return this == DARK ? 0x0B0E13 : 0xF1F3F7;
	}
}
