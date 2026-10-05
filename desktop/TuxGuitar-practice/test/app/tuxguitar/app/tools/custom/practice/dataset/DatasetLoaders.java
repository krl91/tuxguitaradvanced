package app.tuxguitar.app.tools.custom.practice.dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the annotations of the public datasets kept outside the repository (licences forbid
 * redistributing them): IDMT-SMT-Guitar V2 (annotated notes, intervals and chords, XML) and
 * IDMT-SMT-Audio-Effects (notes, intervals and chords whose pitches are coded in the file name,
 * with 11 audio effects). The pitches that sound are the ground truth.
 */
final class DatasetLoaders {

	private static final Pattern PITCH_TAG = Pattern.compile("<pitch>(\\d+)</pitch>");
	private static final Pattern STYLE_TAG = Pattern.compile("<expressionStyle>(\\w+)</expressionStyle>");
	private static final Pattern MONO_NAME = Pattern.compile("^G(\\d)(\\d)-(\\d\\d)(\\d)(\\d\\d)-(\\d)(\\d\\d)(\\d)-\\d+\\.wav$");
	private static final Pattern POLY_NAME = Pattern.compile("^P(\\d)(\\d)-(\\d\\d)(\\d\\d)(\\d)-(\\d)(\\d\\d)(\\d)-\\d+\\.wav$");

	/** Semitones above the lowest note, per polyphony type code of IDMT-SMT-Audio-Effects. */
	private static int[] polyType(int code) {
		switch( code ) {
			case 11: return new int[] { 0, 3 };
			case 12: return new int[] { 0, 4 };
			case 13: return new int[] { 0, 5 };
			case 14: return new int[] { 0, 7 };
			case 15: return new int[] { 0, 10 };
			case 16: return new int[] { 0, 11 };
			case 17: return new int[] { 0, 12 };
			case 21: return new int[] { 0, 4, 7 };
			case 22: return new int[] { 0, 3, 7 };
			case 23: return new int[] { 0, 5, 7 };
			case 24: return new int[] { 0, 7, 12 };
			case 25: return new int[] { 0, 4, 7, 11 };
			case 26: return new int[] { 0, 4, 7, 10 };
			case 27: return new int[] { 0, 3, 7, 10 };
			default: return null;
		}
	}

	private static String polyName(int code) {
		switch( code ) {
			case 11: return "minor third";
			case 12: return "major third";
			case 13: return "perfect fourth";
			case 14: return "perfect fifth";
			case 15: return "minor seventh";
			case 16: return "major seventh";
			case 17: return "octave";
			case 21: return "major triad";
			case 22: return "minor triad";
			case 23: return "sus4 triad";
			case 24: return "power chord";
			case 25: return "major seventh chord";
			case 26: return "dominant seventh chord";
			case 27: return "minor seventh chord";
			default: return "type " + code;
		}
	}

	private static String effectName(int group, int type) {
		switch( type ) {
			case 11: return "no effect";
			case 12: return "amplifier simulation";
			case 21: return "feedback delay";
			case 22: return "slapback delay";
			case 23: return "reverb";
			case 31: return "chorus";
			case 32: return "flanger";
			case 33: return "phaser";
			case 34: return "tremolo";
			case 35: return "vibrato";
			case 41: return "distortion";
			case 42: return "overdrive";
			default: return "effect " + group + type;
		}
	}

	private DatasetLoaders() {
	}

	/** The annotated recordings of IDMT-SMT-Guitar V2 dataset 1 (one XML per recording). */
	static List<DatasetCase> idmtGuitar(Path root, int maxPerFolder) throws IOException {
		List<DatasetCase> cases = new ArrayList<DatasetCase>();
		Path dataset1 = root.resolve("guitar2/IDMT-SMT-GUITAR_V2/dataset1");
		if( !Files.isDirectory(dataset1) ) {
			return cases;
		}
		List<Path> folders;
		try( Stream<Path> stream = Files.list(dataset1) ) {
			folders = stream.filter(Files::isDirectory).sorted().collect(Collectors.toList());
		}
		for( Path folder : folders ) {
			Path annotations = folder.resolve("annotation");
			if( !Files.isDirectory(annotations) ) {
				continue;
			}
			List<Path> xmls;
			try( Stream<Path> stream = Files.list(annotations) ) {
				xmls = stream.filter(p -> p.toString().endsWith(".xml")).sorted().collect(Collectors.toList());
			}
			List<DatasetCase> mine = new ArrayList<DatasetCase>();
			for( Path xml : xmls ) {
				String text = new String(Files.readAllBytes(xml), java.nio.charset.StandardCharsets.UTF_8);
				TreeSet<Integer> pitches = new TreeSet<Integer>();
				Matcher matcher = PITCH_TAG.matcher(text);
				while( matcher.find() ) {
					pitches.add(Integer.parseInt(matcher.group(1)));
				}
				Matcher styles = STYLE_TAG.matcher(text);
				boolean plain = true;
				while( styles.find() ) {
					plain &= "NO".equals(styles.group(1));
				}
				Path audio = folder.resolve("audio").resolve(xml.getFileName().toString().replace(".xml", ".wav"));
				if( pitches.isEmpty() || !Files.isRegularFile(audio) || !plain ) {
					continue;
				}
				DatasetCase.Kind kind = (pitches.size() == 1 ? DatasetCase.Kind.SINGLE_NOTE : pitches.size() == 2 ? DatasetCase.Kind.INTERVAL : DatasetCase.Kind.CHORD);
				mine.add(new DatasetCase("IDMT-SMT-Guitar", folder.getFileName() + "/" + audio.getFileName(), audio,
						pitches.stream().mapToInt(Integer::intValue).toArray(), kind, folder.getFileName().toString(),
						pitches.size() == 1 ? "single note" : folder.getFileName().toString().endsWith("Chords") ? audio.getFileName().toString().replaceAll(" \\d+\\.wav$", "") : pitches.size() + " notes"));
			}
			cases.addAll(sample(mine, maxPerFolder));
		}
		return cases;
	}

	/** IDMT-SMT-Audio-Effects, guitar: monophonic notes ("mono") or intervals and chords ("poly"). */
	static List<DatasetCase> effects(Path root, boolean polyphonic, int maxPerEffect) throws IOException {
		List<DatasetCase> cases = new ArrayList<DatasetCase>();
		Path base = root.resolve("effects/IDMT-SMT-AUDIO-EFFECTS/IDMT-SMT-AUDIO-EFFECTS/" + (polyphonic ? "poly/Gitarre polyphon/Samples" : "mono/Gitarre monophon/Samples"));
		if( !Files.isDirectory(base) ) {
			return cases;
		}
		List<Path> effectFolders;
		try( Stream<Path> stream = Files.list(base) ) {
			effectFolders = stream.filter(Files::isDirectory).sorted().collect(Collectors.toList());
		}
		for( Path folder : effectFolders ) {
			List<DatasetCase> mine = new ArrayList<DatasetCase>();
			List<Path> files;
			try( Stream<Path> stream = Files.list(folder) ) {
				files = stream.filter(p -> p.toString().endsWith(".wav")).sorted().collect(Collectors.toList());
			}
			for( Path file : files ) {
				String name = file.getFileName().toString();
				if( polyphonic ) {
					Matcher m = POLY_NAME.matcher(name);
					if( !m.matches() ) {
						continue;
					}
					int[] steps = polyType(Integer.parseInt(m.group(4)));
					if( steps == null ) {
						continue;
					}
					int lowest = Integer.parseInt(m.group(3));
					int[] pitches = new int[steps.length];
					for( int i = 0 ; i < steps.length ; i ++ ) {
						pitches[i] = lowest + steps[i];
					}
					mine.add(new DatasetCase("IDMT-SMT-Audio-Effects poly", folder.getFileName() + "/" + name, file, pitches,
							pitches.length == 2 ? DatasetCase.Kind.INTERVAL : DatasetCase.Kind.CHORD,
							folder.getFileName() + " / " + effectName(Integer.parseInt(m.group(6)), Integer.parseInt(m.group(7))), polyName(Integer.parseInt(m.group(4)))));
				} else {
					Matcher m = MONO_NAME.matcher(name);
					if( !m.matches() ) {
						continue;
					}
					mine.add(new DatasetCase("IDMT-SMT-Audio-Effects mono", folder.getFileName() + "/" + name, file, new int[] { Integer.parseInt(m.group(3)) },
							DatasetCase.Kind.SINGLE_NOTE, folder.getFileName() + " / " + effectName(Integer.parseInt(m.group(6)), Integer.parseInt(m.group(7))), "single note"));
				}
			}
			if( polyphonic && maxPerEffect > 0 ) {
				// every kind of interval and chord is represented: the recordings are ordered by pitch, then by kind
				java.util.Map<String, List<DatasetCase>> byLabel = new java.util.TreeMap<String, List<DatasetCase>>();
				for( DatasetCase c : mine ) {
					byLabel.computeIfAbsent(c.label, k -> new ArrayList<DatasetCase>()).add(c);
				}
				int perLabel = Math.max(1, maxPerEffect / byLabel.size());
				for( List<DatasetCase> group : byLabel.values() ) {
					cases.addAll(sample(group, perLabel));
				}
			} else {
				cases.addAll(sample(mine, maxPerEffect));
			}
		}
		return cases;
	}

	/** At most {@code max} cases, evenly spread and always the same ones (0 or less: all). */
	static List<DatasetCase> sample(List<DatasetCase> all, int max) {
		if( max <= 0 || all.size() <= max ) {
			return all;
		}
		List<DatasetCase> chosen = new ArrayList<DatasetCase>();
		for( int i = 0 ; i < max ; i ++ ) {
			chosen.add(all.get((int) ((long) i * all.size() / max)));
		}
		Collections.sort(chosen, (a, b) -> a.id.compareTo(b.id));
		return chosen;
	}
}
