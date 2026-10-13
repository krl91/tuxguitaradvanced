package app.tuxguitar.app.tools.custom.practice.branding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The branding assets of spec 008 (FR-014, SC-001, SC-003): the master is the official logo, every surface of the manifest exists, decodes and has the declared
 * format and dimensions, the skins carry the same images, no image of the identity is missing from the manifest and, for a release
 * ({@code -Dbranding.release=true}), every derivative that is used is approved by the owner (FR-010, FR-017). It reads {@code specs/008-brand-identity/branding-manifest.json}.
 */
public class BrandingAssetsTest {

	private static final String MASTER_SHA256 = "a9d369a2574396cd3208b0665648c57f398690499efb137d6781478991baf9e0";

	private static Path root() {
		Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		while (dir != null && !Files.exists(dir.resolve("specs/008-brand-identity/branding-manifest.json"))) {
			dir = dir.getParent();
		}
		Assumptions.assumeTrue(dir != null, "the branding manifest is not in the repository: skipped");
		return dir;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> manifest(Path root) throws Exception {
		return (Map<String, Object>) new Json(new String(Files.readAllBytes(root.resolve("specs/008-brand-identity/branding-manifest.json")), "UTF-8")).value();
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> surfaces(Map<String, Object> manifest) {
		return (List<Map<String, Object>>) manifest.get("surfaces");
	}

	private static String sha256(Path file) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		StringBuilder hex = new StringBuilder();
		for (byte b : digest.digest(Files.readAllBytes(file))) {
			hex.append(String.format("%02x", b));
		}
		return hex.toString();
	}

	@Test
	public void theMasterIsTheOfficialLogo() throws Exception {
		Path root = root();
		Path master = root.resolve("specs/008-brand-identity/assets/tuxguitaradvanced-logo-primary.png");
		assertTrue(Files.exists(master), "the master is in the repository");
		assertEquals(MASTER_SHA256, sha256(master), "the master is byte-identical to the logo selected by the owner (SC-001)");
		BufferedImage image = ImageIO.read(master.toFile());
		assertEquals(1254, image.getWidth());
		assertEquals(1254, image.getHeight());
		assertTrue(image.getColorModel().hasAlpha(), "the master has an alpha channel (transparent background)");
		@SuppressWarnings("unchecked")
		Map<String, Object> declared = (Map<String, Object>) manifest(root).get("master");
		assertEquals(MASTER_SHA256, declared.get("sha256"), "the manifest declares the same digest");
	}

	/** The pixel sizes of the entries of an ICO, read from its header (a width of 0 is 256). */
	private static Set<Integer> icoSizes(Path file) throws Exception {
		ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
		assertEquals(0, b.getShort(0), file + ": reserved");
		assertEquals(1, b.getShort(2), file + ": an icon resource");
		int count = b.getShort(4) & 0xFFFF;
		Set<Integer> sizes = new HashSet<Integer>();
		for (int i = 0; i < count; i++) {
			int w = b.get(6 + 16 * i) & 0xFF;
			int h = b.get(7 + 16 * i) & 0xFF;
			assertEquals(w, h, file + ": a square entry");
			sizes.add(w == 0 ? 256 : w);
		}
		return sizes;
	}

	/** The pixel sizes of the entries of an ICNS, from the types of its chunks. */
	private static Set<Integer> icnsSizes(Path file) throws Exception {
		byte[] data = Files.readAllBytes(file);
		ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
		assertEquals("icns", new String(data, 0, 4, "US-ASCII"), file + ": an icns file");
		Map<String, Integer> pixels = new LinkedHashMap<String, Integer>();
		pixels.put("ic04", 16); pixels.put("ic05", 32); pixels.put("is32", 16); pixels.put("icp4", 16); pixels.put("il32", 32); pixels.put("icp5", 32); pixels.put("ic11", 32);
		pixels.put("ih32", 48); pixels.put("icp6", 64); pixels.put("ic12", 64); pixels.put("ic07", 128); pixels.put("it32", 128);
		pixels.put("ic08", 256); pixels.put("ic13", 256); pixels.put("ic09", 512); pixels.put("ic14", 512); pixels.put("ic10", 1024);
		Set<Integer> sizes = new HashSet<Integer>();
		int offset = 8;
		while (offset + 8 <= data.length) {
			String type = new String(data, offset, 4, "US-ASCII");
			int length = b.getInt(offset + 4);
			assertTrue(length >= 8, file + ": a chunk has a length");
			if (pixels.containsKey(type)) {
				sizes.add(pixels.get(type));
			}
			offset += length;
		}
		return sizes;
	}

	@Test
	public void everyDeclaredSurfaceExistsDecodesAndHasItsFormatAndDimensions() throws Exception {
		Path root = root();
		List<String> problems = new ArrayList<String>();
		for (Map<String, Object> surface : surfaces(manifest(root))) {
			String format = (String) surface.get("format");
			if ("markdown".equals(format)) {
				continue;
			}
			Path file = root.resolve((String) surface.get("path"));
			String id = surface.get("id") + " " + surface.get("path");
			if (!Files.isReadable(file)) {
				problems.add(id + ": missing or unreadable");
				continue;
			}
			if ("png".equals(format)) {
				BufferedImage image = ImageIO.read(file.toFile());
				if (image == null) {
					problems.add(id + ": not a readable PNG");
				} else if (image.getWidth() != ((Number) surface.get("width")).intValue() || image.getHeight() != ((Number) surface.get("height")).intValue()) {
					problems.add(id + ": " + image.getWidth() + "x" + image.getHeight() + " instead of " + surface.get("width") + "x" + surface.get("height"));
				}
			} else if ("ico".equals(format) || "icns".equals(format)) {
				Set<Integer> have = "ico".equals(format) ? icoSizes(file) : icnsSizes(file);
				for (Object declared : (List<?>) surface.get("entries")) {
					if (!have.contains(((Number) declared).intValue())) {
						problems.add(id + ": no entry of " + declared + " px (has " + have + ")");
					}
				}
			} else {
				problems.add(id + ": unknown format " + format);
			}
		}
		assertTrue(problems.isEmpty(), "the declared assets are not all as declared:\n" + String.join("\n", problems));
	}

	@Test
	public void theSkinsCarryTheSameIconAndTheSameSplashAndTheirSourcesAreTheSame() throws Exception {
		Path root = root();
		for (String name : new String[] { "icon.png", "splash.png" }) {
			String reference = null;
			for (File dir : root.resolve("desktop/TuxGuitar/share/skins").toFile().listFiles(File::isDirectory)) {
				Path file = dir.toPath().resolve(name);
				if (Files.exists(file)) {
					String digest = sha256(file);
					reference = reference == null ? digest : reference;
					assertEquals(reference, digest, file + " is the same image in every skin");
				}
			}
			for (Map<String, Object> surface : surfaces(manifest(root))) {
				if (((String) surface.get("path")).endsWith("/" + name)) {
					assertEquals(reference, sha256(root.resolve((String) surface.get("path"))), surface.get("path") + " equals the skins");
				}
			}
		}
	}

	@Test
	public void noImageOfTheIdentityIsMissingFromTheManifest() throws Exception {
		Path root = root();
		Set<String> declared = new HashSet<String>();
		for (Map<String, Object> surface : surfaces(manifest(root))) {
			declared.add((String) surface.get("path"));
		}
		List<String> missing = new ArrayList<String>();
		Path common = root.resolve("desktop/build-scripts/common-resources");
		List<Path> candidates = new ArrayList<Path>();
		try (java.util.stream.Stream<Path> walk = Files.walk(common)) {
			walk.filter(p -> {
				String n = p.getFileName().toString();
				return Files.isRegularFile(p) && (n.equals("icon.icns") || n.endsWith(".ico") || n.startsWith("TG_") || n.equals("tuxguitar.png"));
			}).forEach(candidates::add);
		}
		try (java.util.stream.Stream<Path> walk = Files.walk(root.resolve("desktop/TuxGuitar"))) {
			walk.filter(p -> Files.isRegularFile(p) && (p.getFileName().toString().equals("icon.png") || p.getFileName().toString().equals("splash.png"))
					&& (p.toString().contains("/share/skins/") || p.toString().contains("/src-skins/"))).forEach(candidates::add);
		}
		for (Path candidate : candidates) {
			String relative = root.relativize(candidate).toString();
			if (!declared.contains(relative)) {
				missing.add(relative);
			}
		}
		assertTrue(missing.isEmpty(), "images of the identity that the manifest does not declare (no silent replacement):\n" + String.join("\n", missing));
	}

	@Test
	public void theDocumentationShowsTheMasterByItsPathAndNotACopyOfIt() throws Exception {
		Path root = root();
		String path = "specs/008-brand-identity/assets/tuxguitaradvanced-logo-primary.png";
		for (Map<String, Object> surface : surfaces(manifest(root))) {
			if (!"markdown".equals(surface.get("format"))) {
				continue;
			}
			Path file = root.resolve((String) surface.get("path"));
			if (!Files.exists(file)) {
				// the release notes of a version that is not written yet are not checked
				continue;
			}
			String text = new String(Files.readAllBytes(file), "UTF-8");
			assertTrue(text.contains(path), surface.get("path") + " shows the master by its path (US1, FR-001)");
		}
		// no second copy of the master: the only file with its digest is the master itself (SC-001)
		int copies = 0;
		try (java.util.stream.Stream<Path> walk = Files.walk(root.resolve("docs"))) {
			for (Path p : (Iterable<Path>) walk.filter(q -> q.toString().endsWith(".png"))::iterator) {
				if (MASTER_SHA256.equals(sha256(p))) {
					copies++;
				}
			}
		}
		assertEquals(0, copies, "no copy of the master in docs/");
	}

	@Test
	public void theWindowsInstallersReadExactlyTheDeclaredImagesAndIcons() throws Exception {
		Path root = root();
		Map<String, String> declared = new LinkedHashMap<String, String>();
		for (Map<String, Object> surface : surfaces(manifest(root))) {
			declared.put((String) surface.get("id"), (String) surface.get("path"));
		}
		// the installer tag -> the surface of the manifest (S3 to S7)
		Map<String, String> tags = new LinkedHashMap<String, String>();
		tags.put("windowsExecutableIcon", "S3");
		tags.put("windowsUninstallerExecutableIcon", "S4");
		tags.put("logoImage", "S5");
		tags.put("leftImage", "S6");
		tags.put("splashImage", "S7");
		for (String project : new String[] { "desktop/build-scripts/tuxguitar-windows-swt-x86_64-installer/src/installbuilder/project.xml",
				"desktop/build-scripts/tuxguitar-windows-jfx-x86_64-installer/src/installbuilder/project.xml" }) {
			Path file = root.resolve(project);
			String xml = new String(Files.readAllBytes(file), "UTF-8");
			for (Map.Entry<String, String> tag : tags.entrySet()) {
				java.util.regex.Matcher m = java.util.regex.Pattern.compile("<" + tag.getKey() + ">([^<]+)</" + tag.getKey() + ">").matcher(xml);
				assertTrue(m.find(), project + " names a file in <" + tag.getKey() + ">");
				Path named = file.getParent().resolve(m.group(1).trim()).normalize();
				assertTrue(Files.exists(named), project + ": " + named + " exists");
				assertEquals(root.resolve(declared.get(tag.getValue())).normalize(), named, project + ": <" + tag.getKey() + "> is the surface " + tag.getValue() + " of the manifest");
			}
		}
	}

	@Test
	public void aReleaseCarriesOnlyApprovedDerivatives() throws Exception {
		Assumptions.assumeTrue(Boolean.getBoolean("branding.release"), "only checked for a release (-Dbranding.release=true)");
		Path root = root();
		Map<String, Object> manifest = manifest(root);
		Map<String, String> status = new LinkedHashMap<String, String>();
		for (Object d : (List<?>) manifest.get("derivatives")) {
			@SuppressWarnings("unchecked")
			Map<String, Object> derivative = (Map<String, Object>) d;
			status.put((String) derivative.get("id"), (String) derivative.get("status"));
		}
		List<String> notApproved = new ArrayList<String>();
		for (Map<String, Object> surface : surfaces(manifest(root))) {
			String used = String.valueOf(surface.get("derivative"));
			for (Map.Entry<String, String> e : status.entrySet()) {
				if (used.contains(e.getKey()) && !"approved".equals(e.getValue())) {
					notApproved.add(surface.get("id") + " uses " + e.getKey() + " (" + e.getValue() + ")");
				}
			}
		}
		assertTrue(notApproved.isEmpty(), "derivatives that the owner has not approved are used:\n" + String.join("\n", notApproved));
	}

	/** A small JSON reader for the manifest: objects, arrays, strings, numbers, booleans and null. */
	private static final class Json {
		private final String s;
		private int i;

		Json(String s) {
			this.s = s;
		}

		Object value() {
			ws();
			char c = s.charAt(i);
			if (c == '{') {
				Map<String, Object> map = new LinkedHashMap<String, Object>();
				i++;
				ws();
				if (s.charAt(i) == '}') { i++; return map; }
				while (true) {
					ws();
					String key = string();
					ws(); i++; // the colon
					map.put(key, value());
					ws();
					if (s.charAt(i++) == '}') { return map; }
				}
			}
			if (c == '[') {
				List<Object> list = new ArrayList<Object>();
				i++;
				ws();
				if (s.charAt(i) == ']') { i++; return list; }
				while (true) {
					list.add(value());
					ws();
					if (s.charAt(i++) == ']') { return list; }
				}
			}
			if (c == '"') {
				return string();
			}
			if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
			if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
			if (s.startsWith("null", i)) { i += 4; return null; }
			int start = i;
			while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) { i++; }
			return Double.valueOf(s.substring(start, i));
		}

		private String string() {
			StringBuilder out = new StringBuilder();
			i++;
			while (s.charAt(i) != '"') {
				char c = s.charAt(i++);
				if (c == '\\') {
					char n = s.charAt(i++);
					out.append(n == 'n' ? '\n' : n == 't' ? '\t' : n == 'u' ? (char) Integer.parseInt(s.substring(i, i += 4), 16) : n);
				} else {
					out.append(c);
				}
			}
			i++;
			return out.toString();
		}

		private void ws() {
			while (i < s.length() && Character.isWhitespace(s.charAt(i))) { i++; }
		}
	}
}
