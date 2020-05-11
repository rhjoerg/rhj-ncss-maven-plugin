package ch.rhj.maven.ncss;

import javax.inject.Inject;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "rhj-ncss", defaultPhase = LifecyclePhase.COMPILE)
public class NCSSMojo extends AbstractMojo {

	@Inject
	public Model model;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		getLog().info(String.valueOf(model));
		getLog().info("rhj-ncss (1) executed");
	}
}
