package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** The real {@link CommandRunner}: a process of the system, given up and destroyed when it takes longer than the limit. */
public final class ProcessCommandRunner implements CommandRunner {

	@Override
	public Result run(String[] command, long timeoutMillis) throws IOException, InterruptedException, TimeoutException {
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		try {
			if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
				throw new TimeoutException(command[0] + " took more than " + timeoutMillis + " ms");
			}
			return new Result(process.exitValue(), read(process.getInputStream()));
		} finally {
			process.destroyForcibly();
		}
	}

	/** The answers read here are a few characters: the process has ended, so nothing is left to wait for. */
	private static String read(InputStream stream) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[256];
		int count;
		while (out.size() < 4096 && (count = stream.read(buffer)) > 0) {
			out.write(buffer, 0, count);
		}
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}
}
