# Contract: the new categories of the Recognition settings window (spec 026, FR-005, FR-005a, SC-007)

Window: TuxGuitar Configuration › *Audio recognition* (`AudioRecognitionOption`, `RecognitionSettingsPanel`), a *Category* drop-down with one panel per `RecognitionParameter.Section`.

- The 8 existing categories keep their order, names and content: Input, Tuner, Notes, Chords, Chord details, Passages, Recognition engine, Advanced.
- The new categories follow, in the order of data-model.md, each named "Advanced: <family>" with "(1/2)" etc. when split.
  - English "Advanced: Onsets", French "Avancé : Attaques", Spanish "Avanzado: Ataques"; the exact names are in the message files, keys `recognition.section.<section>`.
- Each category: at most 7 values and its *Restore* button (`RecognitionControls.MAX_SECTION_ROWS` = 8), as every category today; each value a slider (number), check box (flag) or list (choice) with its value label "x unit (recommended: d unit)", a name and a tip in three languages.
  - The tip says what the value does, which way is more lenient or more sensitive, and "Expert setting: the recommended value was measured; change it only to test".
  - The tip states no number with a unit (cents, ms, dB, %): ranges and recommended values come from the parameter (committed `RecognitionControlsTest.tooltipsDoNotRepeatNumbersThatComeFromTheParameters`).
  - In French, every name, tip and category name differs from the English one (committed `RecognitionSettingsPanelTest`, only *Passages* is the same word): "YIN" alone is not a name, "Seuil YIN" is. Only the Spanish texts may share an exception list (T011).
- *Restore* of a category restores its values; *Restore all* restores every value, the new ones included.
- Fit: on a screen whose usable height is 768 pixels, every category's every control and its *Restore* button are inside the window, without scrolling (new SWT test, as spec 025's `PracticeWindowFitsSwtTest`).
- The help pages `desktop/TuxGuitar/share/help/tools_audio_recognition{,_fr,_es}.html` get one `<h2>` heading per new section, with the exact category name of its language, and one row `<tr><td>name</td>…` per parameter whose third, fourth and fifth cells are the minimum, the maximum and the recommended value as the window shows them (`RecognitionControls.displayNumber`), as the committed help check reads for English and French, as for the 34 settings today.
- Nothing in the practice window changes.
