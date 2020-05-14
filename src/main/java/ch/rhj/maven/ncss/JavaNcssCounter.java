package ch.rhj.maven.ncss;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.codehaus.plexus.component.annotations.Component;

import ch.rhj.util.io.IO;

@Component(role = NcssCounter.class)
public class JavaNcssCounter implements NcssCounter {

	private final static String[] EXTENSIONS = { ".java" };

	@Override
	public Stream<String> extensions() {

		return Stream.of(EXTENSIONS);
	}

	@Override
	public int count(Path source, Charset charset) {

		String java = IO.readString(source, charset);

		return count(java, ';') + count(java, '{');
	}
}
