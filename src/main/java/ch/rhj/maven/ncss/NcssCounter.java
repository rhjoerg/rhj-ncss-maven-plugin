package ch.rhj.maven.ncss;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.stream.Stream;

public interface NcssCounter {

	public Stream<String> extensions();

	public int count(Path source, Charset charset);

	default int count(String source, char character) {

		int count = 0;
		int start = 0;
		int pos;

		while ((pos = source.indexOf(character, start)) >= 0) {

			++count;
			start = pos + 1;
		}

		return count;
	}
}
