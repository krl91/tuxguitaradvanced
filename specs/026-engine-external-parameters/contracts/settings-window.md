# Contract: the new categories of the Recognition settings window (spec 026, FR-005, FR-005a, SC-007)

Window: TuxGuitar Configuration › *Audio recognition* (`AudioRecognitionOption`, `RecognitionSettingsPanel`), a *Category* drop-down with one panel per `RecognitionParameter.Section`.

- The 8 existing categories keep their order, names and content: Input, Tuner, Notes, Chords, Chord details, Passages, Recognition engine, Advanced.
- The new categories follow, in the order of data-model.md, each named "Advanced: <family>" with "(1/2)" etc. when split.
  - English "Advanced: Onsets", French "Avancé : Attaques", Spanish "Avanzado: Ataques"; the exact names are in the message files, keys `recognition.section.<section>`.
- Each category: at most 7 values and its *Restore* button (`RecognitionControls.MAX_SECTION_ROWS` = 8), as every category today; each value a slider (number), check box (flag) or list (choice) with its value label "x unit (recommended: d unit)", a name and a tip in three languages.
  - The tip says what the value does, which way is more lenient or more sensitive, and "Expert setting: the recommended value was measured; change it only to test".
- *Restore* of a category restores its values; *Restore all* restores every value, the new ones included.
- Fit: on a screen whose usable height is 768 pixels, every category's every control and its *Restore* button are inside the window, without scrolling (new SWT test, as spec 025's `PracticeWindowFitsSwtTest`).
- Nothing in the practice window changes.
