package ch.rhj.maven.ncss;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.maven.Maven;
import org.apache.maven.cli.CLIManager;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.cli.logging.Slf4jLoggerManager;
import org.apache.maven.cli.logging.Slf4jStdoutLogger;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.extension.internal.CoreExports;
import org.apache.maven.extension.internal.CoreExtensionEntry;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.toolchain.building.ToolchainsBuilder;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.logging.LoggerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import com.google.inject.AbstractModule;

import ch.rhj.util.Threads;
import ch.rhj.util.config.Env;
import ch.rhj.util.config.Sys;
import ch.rhj.util.io.IO;
import ch.rhj.util.io.Zip;
import ch.rhj.util.test.TestPaths;

public class NCSSMojoTests implements TestPaths {

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
	public void testCompile1() throws Exception {

		System.out.println("---------- COMPILE 1 ----------");

		String workingDirectory = helloOutput.toString();
		Sys.setIfAbsent("maven.multiModuleProjectDirectory", workingDirectory);

		ClassWorld classWorld = new ClassWorld("plexus.core", Threads.contextClassLoader());
		MavenCli mavenCli = new MavenCli(classWorld);
		String[] args = { "compile" };

		mavenCli.doMain(args, workingDirectory, System.out, System.err);
	}

	@Test
	public void testCompile2() throws Exception {

		System.out.println("---------- COMPILE 2 ----------");

		String workingDirectory = helloOutput.toString();
		File multiModuleProjectDirectory = helloOutput.toFile();
		Sys.setIfAbsent("maven.multiModuleProjectDirectory", workingDirectory);

		ClassWorld classWorld = new ClassWorld("plexus.core", Threads.contextClassLoader());
		String[] args = { "compile" };
		MavenExecutionRequest request = new DefaultMavenExecutionRequest();
		Properties userProperties = new Properties();
		Properties systemProperties = new Properties();
		PlexusContainer localContainer = null;
		Logger slf4jLogger = new Slf4jStdoutLogger();
		CLIManager cliManager = new CLIManager();
		CommandLine commandLine = cliManager.parse(args);
		Properties buildProperties = IO.properties(IO.classLoaderPath("org/apache/maven/messages/build.properties"));
		String distributionName = buildProperties.getProperty("distributionName");
		String buildVersion = buildProperties.getProperty("version");
		String buildNumber = buildProperties.getProperty("buildNumber");
		String mavenBuildVersion = String.format("%1$s %2$s (%3$s)", distributionName, buildVersion, buildNumber);

		systemProperties.putAll(Env.asProperties("env."));
		systemProperties.putAll(Sys.copy());
		systemProperties.setProperty("maven.version", buildVersion);
		systemProperties.setProperty("maven.build.version", mavenBuildVersion);

		ILoggerFactory slf4jLoggerFactory = LoggerFactory.getILoggerFactory();
		LoggerManager plexusLoggerManager = new Slf4jLoggerManager();
		slf4jLogger = slf4jLoggerFactory.getLogger(this.getClass().getName());

		ClassRealm coreRealm = classWorld.getClassRealm("plexus.core");
		ClassRealm containerRealm = coreRealm; // may be "maven.ext" if extensions are set in .mvn/extensions.xml
		CoreExtensionEntry coreEntry = CoreExtensionEntry.discoverFrom(coreRealm);

		ContainerConfiguration containerConfiguration = new DefaultContainerConfiguration() //
				.setClassWorld(classWorld) //
				.setRealm(containerRealm) //
				.setClassPathScanning(PlexusConstants.SCANNING_INDEX) //
				.setAutoWiring(true) //
				.setJSR250Lifecycle(true) //
				.setName("maven");

		Set<String> exportedArtifacts = new HashSet<>(coreEntry.getExportedArtifacts());
		Set<String> exportedPackages = new HashSet<>(coreEntry.getExportedPackages());
		CoreExports exports = new CoreExports(containerRealm, exportedArtifacts, exportedPackages);

		DefaultPlexusContainer container = new DefaultPlexusContainer(containerConfiguration, new AbstractModule() {

			@Override
			protected void configure() {

				bind(ILoggerFactory.class).toInstance(slf4jLoggerFactory);
				bind(CoreExports.class).toInstance(exports);
			}
		});

		container.setLookupRealm(null);
		// Thread.currentThread().setContextClassLoader(container.getContainerRealm()); // bad idea at this point
		container.setLoggerManager(plexusLoggerManager);
		container.getLoggerManager().setThresholds(request.getLoggingLevel());

		slf4jLogger = slf4jLoggerFactory.getLogger(this.getClass().getName());

		Maven maven = container.lookup(Maven.class);
		MavenExecutionRequestPopulator executionRequestPopulator = container.lookup(MavenExecutionRequestPopulator.class);
		ModelProcessor modelProcessor = container.lookup(ModelProcessor.class);
		Map<String, ConfigurationProcessor> configurationProcessors = container.lookupMap(ConfigurationProcessor.class);
		ToolchainsBuilder toolchainsBuilder = container.lookup(ToolchainsBuilder.class);
		DefaultSecDispatcher dispatcher = (DefaultSecDispatcher) container.lookup(SecDispatcher.class, "maven");

		// TODO: read ".mvn/maven.config" in target project directory
		// TODO: merge ".mvn/maven.config", see MavenCli.cli
		// TODO: copy command line properties to systemProperties, see MavenCli.populateProperties
		// TODO: configure logging according to command line, see MavenCli.logging
		// TODO: resolve "maven.ext.classpath", see MavenCli.container
		// TODO: honor ".mvn/extensions.xml", see MavenCli.container
		// TODO: configure event spy, see MavenCli.container

		// see MavenCli.doMain(CliRequest)
//        localContainer = container( cliRequest );
//        commands( cliRequest );
//        configure( cliRequest );
//        toolchains( cliRequest );
//        populateRequest( cliRequest );
//        encryption( cliRequest );
//        repository( cliRequest );
//        return execute( cliRequest );
	}
}
