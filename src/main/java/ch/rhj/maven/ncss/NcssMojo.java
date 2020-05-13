package ch.rhj.maven.ncss;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.inject.Inject;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.rhj.util.Ex;
import ch.rhj.util.io.IO;

@Mojo(name = "ncss", defaultPhase = LifecyclePhase.COMPILE)
public class NcssMojo extends AbstractMojo {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Data {

		@JsonProperty
		public int schemaVersion = 1;

		@JsonProperty
		public String label = "NCSS";

		@JsonProperty
		public String message;

		@JsonProperty
		public String color = "informational";

		public Data(int ncss) {

			this.message = Integer.toString(ncss);
		}
	}

	@Inject
	private MavenProject project;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		Charset charset = Charset.forName(project.getProperties().getProperty("project.build.sourceEncoding"));

		List<String> rootNames = project.getCompileSourceRoots();
		List<Path> rootPaths = rootNames.stream().map(n -> Paths.get(n)).collect(toList());
		int count = 0;

		for (Path start : rootPaths) {

			count += IO.findFilesWithExtension(start, false, "java", Integer.MAX_VALUE) //
					.mapToInt(p -> ncss(p, charset)).sum();
		}

		Data data = new Data(count);
		ObjectMapper mapper = new ObjectMapper();
		String json = Ex.supply(() -> mapper.writeValueAsString(data));

		IO.write(json.getBytes(UTF_8), project.getBasedir().toPath().resolve("ncss.json"), true);
	}

	private int ncss(Path path, Charset charset) {

		char[] chars = { ';', '{' };
		String source = IO.readString(path, charset);
		int count = 0;
		int start = 0;
		int pos = -1;

		for (char c : chars) {

			start = 0;

			while ((pos = source.indexOf(c, start)) >= 0) {

				++count;
				start = pos + 1;
			}
		}

		return count;
	}
}
