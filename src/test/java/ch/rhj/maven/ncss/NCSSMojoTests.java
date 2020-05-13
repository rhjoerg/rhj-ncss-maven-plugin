package ch.rhj.maven.ncss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.maven.Maven;
import org.apache.maven.bridge.MavenRepositorySystem;
import org.apache.maven.cli.CLIManager;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.cli.event.ExecutionEventLogger;
import org.apache.maven.cli.logging.Slf4jLoggerManager;
import org.apache.maven.cli.transfer.Slf4jMavenTransferListener;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.extension.internal.CoreExports;
import org.apache.maven.extension.internal.CoreExtensionEntry;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.SettingsUtils;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.logging.LoggerManager;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.transfer.TransferListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;

import ch.rhj.util.Ex;
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
		int result = mavenCli.doMain(args, workingDirectory, System.out, System.err);

		assertEquals(0, result);
	}

	// @Test
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
//		PlexusContainer localContainer = null;
//		Logger slf4jLogger = new Slf4jStdoutLogger();
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
//		slf4jLogger = slf4jLoggerFactory.getLogger(this.getClass().getName());

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

//		slf4jLogger = slf4jLoggerFactory.getLogger(this.getClass().getName());

		Maven maven = container.lookup(Maven.class);
		MavenExecutionRequestPopulator executionRequestPopulator = container.lookup(MavenExecutionRequestPopulator.class);
		ModelProcessor modelProcessor = container.lookup(ModelProcessor.class);
//		Map<String, ConfigurationProcessor> configurationProcessors = container.lookupMap(ConfigurationProcessor.class);
//		ToolchainsBuilder toolchainsBuilder = container.lookup(ToolchainsBuilder.class);
//		DefaultSecDispatcher dispatcher = (DefaultSecDispatcher) container.lookup(SecDispatcher.class, "maven");

		File globalSettingsFile = new File(System.getProperty("maven.conf"), "settings.xml");
		File userSettingsFile = Sys.userHomeDirectory().resolve(".m2/settings.xml").toFile();

		request.setGlobalSettingsFile(globalSettingsFile);
		request.setUserSettingsFile(userSettingsFile);

		SettingsBuildingRequest settingsRequest = new DefaultSettingsBuildingRequest();

		settingsRequest.setGlobalSettingsFile(globalSettingsFile);
		settingsRequest.setUserSettingsFile(userSettingsFile);
		settingsRequest.setSystemProperties(systemProperties);
		settingsRequest.setUserProperties(userProperties);

		SettingsBuilder settingsBuilder = container.lookup(SettingsBuilder.class);
		SettingsBuildingResult settingsResult = settingsBuilder.build(settingsRequest);
		Settings settings = settingsResult.getEffectiveSettings();

		request.setOffline(settings.isOffline());
		request.setInteractiveMode(settings.isInteractiveMode());
		request.setPluginGroups(settings.getPluginGroups());
		request.setLocalRepositoryPath(settings.getLocalRepository());

		settings.getServers().stream().map(s -> s.clone()).forEach(s -> request.addServer(s));
		settings.getProxies().stream().filter(p -> p.isActive()).map(p -> p.clone()).forEach(p -> request.addProxy(p));
		settings.getMirrors().stream().map(m -> m.clone()).forEach(m -> request.addMirror(m));
		request.setActiveProfiles(settings.getActiveProfiles());

		for (org.apache.maven.settings.Profile rawProfile : settings.getProfiles()) {

			request.addProfile(SettingsUtils.convertFromSettingsProfile(rawProfile));

			if (settings.getActiveProfiles().contains(rawProfile.getId())) {

				rawProfile.getRepositories().stream() //
						.map(r -> Ex.supply(() -> MavenRepositorySystem.buildArtifactRepository(r))) //
						.forEach(r -> request.addRemoteRepository(r));

				rawProfile.getPluginRepositories().stream() //
						.map(r -> Ex.supply(() -> MavenRepositorySystem.buildArtifactRepository(r))) //
						.forEach(r -> request.addPluginArtifactRepository(r));
			}
		}

		File baseDirectory = new File(workingDirectory, "").getAbsoluteFile();
		List<String> goals = commandLine.getArgList();
		ExecutionListener executionListener = new ExecutionEventLogger();
		TransferListener transferListener = new Slf4jMavenTransferListener();
		File pom = modelProcessor.locatePom(baseDirectory);

		request.setBaseDirectory(baseDirectory) //
				.setGoals(goals) //
				.setSystemProperties(systemProperties) //
				.setUserProperties(userProperties) //
				.setReactorFailureBehavior(MavenExecutionRequest.REACTOR_FAIL_FAST) //
				.setRecursive(true) //
				.setShowErrors(false) //
				.addActiveProfiles(new ArrayList<>()) //
				.addInactiveProfiles(new ArrayList<>()) //
				.setExecutionListener(executionListener) //
				.setTransferListener(transferListener) //
				.setUpdateSnapshots(false) //
				.setNoSnapshotUpdates(false) //
				.setGlobalChecksumPolicy(null) //
				.setPom(pom) //
				.setBaseDirectory(pom.getParentFile()) //
				.setCacheNotFound(true) //
				.setCacheTransferError(false) //
				.setMultiModuleProjectDirectory(multiModuleProjectDirectory);

		MavenExecutionRequest executionRequest = executionRequestPopulator.populateDefaults(request);

		container.lookupMap(ArtifactResolver.class).forEach((k, v) -> System.out.println(k + " " + v));

		MavenExecutionResult result = maven.execute(executionRequest);

		result.getExceptions().forEach(e -> e.printStackTrace());

		// TODO: read ".mvn/maven.config" in target project directory
		// TODO: merge ".mvn/maven.config", see MavenCli.cli
		// TODO: copy command line properties to systemProperties, see MavenCli.populateProperties
		// TODO: configure logging according to command line, see MavenCli.logging
		// TODO: resolve "maven.ext.classpath", see MavenCli.container
		// TODO: honor ".mvn/extensions.xml", see MavenCli.container
		// TODO: configure event spy, see MavenCli.container
		// TODO: honor toolchains
	}
}
