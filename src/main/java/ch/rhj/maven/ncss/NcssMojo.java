package ch.rhj.maven.ncss;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.charset.Charset;
import java.nio.file.Path;

import javax.inject.Inject;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.rhj.util.Ex;
import ch.rhj.util.io.IO;

@Mojo(name = "ncss", defaultPhase = LifecyclePhase.COMPILE)
public class NcssMojo extends AbstractMojo {

	private final Charset charset;
	private final Path basePath;
	private final Path targetPath;
	private final String outputName;
	private final Path outputPath;

	@Inject
	public NcssMojo(MavenProject project) {

		this.charset = Charset.forName(project.getProperties().getProperty("project.build.sourceEncoding", "UTF-8"));
		this.basePath = project.getBasedir().toPath();
		this.targetPath = this.basePath.resolve("target");
		this.outputName = project.getArtifactId() + "-ncss.json";
		this.outputPath = targetPath.resolve(outputName);
	}

	private int count(NcssCounter counter, String ext) {

		return IO.findFilesWithExtension(basePath, false, ext, Integer.MAX_VALUE) //
				.filter(p -> !p.startsWith(targetPath)) //
				.mapToInt(p -> counter.count(p, charset)) //
				.sum();
	}

	private int count(NcssCounter counter) {

		return counter.extensions().mapToInt(ext -> count(counter, ext)).sum();
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		int count = NcssCounters.counters().mapToInt(this::count).sum();

		NcssData data = new NcssData(count);
		ObjectMapper mapper = new ObjectMapper();
		String json = Ex.supply(() -> mapper.writeValueAsString(data));

		IO.write(json.getBytes(UTF_8), outputPath, true);
	}
}
