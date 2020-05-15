package ch.rhj.maven.ncss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.cli.MavenCli;
import org.apache.maven.profiles.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Logger;
import ch.rhj.util.config.Sys;
import ch.rhj.util.io.IO;
import ch.rhj.util.io.compression.Zip;
import ch.rhj.util.test.TestPaths;

public class NcssMojoTests implements TestPaths {

	// make someone happy
	@SuppressWarnings("unused")
	private static final Class<?> LOGGER_CLASS = Logger.class;
	@SuppressWarnings("unused")
	private static final Class<?> PROFILE_CLASS = Profile.class;
	// end of happy

	private static final String[] HELLO_FILES = { "pom.xml", "src/main/java/ch/rhj/hello/Hello.java" };

	private final Path helloInput = inputPath("rhj-hello");
	private final Path helloOutput = outputPath("rhj-hello");

	@BeforeEach
	public void setup() {

		IO.delete(helloOutput);

		for (String file : HELLO_FILES) {

			Path source = helloInput.resolve(file);
			Path target = helloOutput.resolve(file);

			IO.copy(source, target, true);
		}
	}

	public void findJar(String fqcn) {

		String path = fqcn.replace('.', '/') + ".class";
		Path start = Paths.get("C:", "opt", "apache-maven-3.6.3", "lib");

		IO.findFilesWithExtension(start, false, "jar", 1) //
				.filter(p -> Zip.names(p).contains(path)) //
				.forEach(p -> System.out.println(p));
	}

	@Test
	public void testCompile() throws Exception {

		String[] preparationArgs = { //
				"plugin:descriptor", "plugin:addPluginArtifactMetadata", //
				"jar:jar", "install:install" };

		MavenCli mavenCli = new MavenCli();
		String[] compileArgs = { "compile" };
		String workingDirectory;
		int result;

		workingDirectory = Sys.workingDirectory().toString();
		System.setProperty("maven.multiModuleProjectDirectory", workingDirectory);
		result = mavenCli.doMain(preparationArgs, workingDirectory, System.out, System.err);
		assertEquals(0, result);

		workingDirectory = helloOutput.toString();
		System.setProperty("maven.multiModuleProjectDirectory", workingDirectory);
		result = mavenCli.doMain(compileArgs, workingDirectory, System.out, System.err);
		assertEquals(0, result);
	}

}
