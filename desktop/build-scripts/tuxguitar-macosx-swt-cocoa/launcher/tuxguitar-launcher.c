#include <errno.h>
#include <limits.h>
#include <mach-o/dyld.h>
#include <spawn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>

extern char **environ;

int main(int argc, char **argv) {
	uint32_t executableSize = 0;
	_NSGetExecutablePath(NULL, &executableSize);
	char *executable = malloc(executableSize);
	if (executable == NULL || _NSGetExecutablePath(executable, &executableSize) != 0) {
		free(executable);
		return 126;
	}

	char resolved[PATH_MAX];
	if (realpath(executable, resolved) == NULL) {
		free(executable);
		return 126;
	}
	free(executable);

	char *separator = strrchr(resolved, '/');
	if (separator == NULL) {
		return 126;
	}
	*separator = '\0';

	char script[PATH_MAX];
	/* macOS accepts only executables in Contents/MacOS: the application (JRE, jars, resources, tuxguitar.sh) is in Contents/Resources/tuxguitar */
	if (snprintf(script, sizeof(script), "%s/../Resources/tuxguitar/tuxguitar.sh", resolved) >= (int) sizeof(script)) {
		return 126;
	}

	char **childArgv = calloc((size_t) argc + 2, sizeof(char *));
	if (childArgv == NULL) {
		return 126;
	}
	childArgv[0] = "/bin/sh";
	childArgv[1] = script;
	for (int i = 1; i < argc; i++) {
		childArgv[i + 1] = argv[i];
	}

	pid_t child;
	int spawnResult = posix_spawn(&child, "/bin/sh", NULL, NULL, childArgv, environ);
	free(childArgv);
	if (spawnResult != 0) {
		return 126;
	}

	int status;
	while (waitpid(child, &status, 0) == -1) {
		if (errno != EINTR) {
			return 1;
		}
	}
	if (WIFEXITED(status)) {
		return WEXITSTATUS(status);
	}
	if (WIFSIGNALED(status)) {
		return 128 + WTERMSIG(status);
	}
	return 1;
}
