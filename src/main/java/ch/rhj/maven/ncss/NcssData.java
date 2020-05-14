package ch.rhj.maven.ncss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NcssData {

	@JsonProperty
	public int schemaVersion = 1;

	@JsonProperty
	public String label = "NCSS";

	@JsonProperty
	public String message;

	@JsonProperty
	public String color = "informational";

	public NcssData(int ncss) {

		this.message = Integer.toString(ncss);
	}
}
