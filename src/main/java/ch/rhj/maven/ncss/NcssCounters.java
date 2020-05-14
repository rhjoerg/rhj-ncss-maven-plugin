package ch.rhj.maven.ncss;

import java.util.stream.Stream;

public interface NcssCounters {

	public final static NcssCounter JAVA_COUNTER = new JavaNcssCounter();

	public static Stream<NcssCounter> counters() {

		return Stream.of(JAVA_COUNTER);
	}
}
