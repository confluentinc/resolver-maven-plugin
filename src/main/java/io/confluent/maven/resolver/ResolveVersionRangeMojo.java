package io.confluent.maven.resolver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

/**
 * Resolves a version range to the highest matching version. By default, snapshots are excluded, as
 * a workaround for <a href="https://issues.apache.org/jira/browse/MNG-3092">MNG-3092</a>.
 *
 * <p>
 * For the version range format, see <a href=
 * "https://github.com/eclipse/aether-core/blob/master/aether-util/src/main/java/org/eclipse/aether/util/version/GenericVersionScheme.java">
 * GenericVersionScheme</a>
 * </p>
 *
 * <p>
 * Command-line example:
 * </p>
 *
 * <pre>
 * mvn com.subshell.maven:resolver-maven-plugin:resolve-range \
 *     -Dresolve.groupId=org.apache.maven -Dresolve.artifactId=maven-model "-Dresolve.versionRange=[3.1.0, 3.3.max]" \
 *     -Dresolve.print -q
 * </pre>
 */
@Mojo(name = "resolve-range", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true, requiresProject = false)
public class ResolveVersionRangeMojo extends AbstractMojo {
	private static final String SNAPSHOT = "SNAPSHOT";
	private static final Pattern SNAPSHOT_TIMESTAMP = Pattern.compile("^(.*-)?([0-9]{8}\\.[0-9]{6}-[0-9]+)$");

	/**
	 * The group id of the artifact to resolve.
	 */
	@Parameter(property = "resolve.groupId", required = true)
	private String groupId;

	/**
	 * The artifact id of the artifact to resolve.
	 */
	@Parameter(property = "resolve.artifactId", required = true)
	private String artifactId;

	/**
	 * The version range of the artifact to resolve.
	 */
	@Parameter(property = "resolve.versionRange", required = true)
	private String version;

	/**
	 * If <code>true</code>, the highest matching version is printed directly to the console. This
	 * can be used with {@code mvn -q}.
	 */
	@Parameter(property = "resolve.print")
	private boolean print;

	/**
	 * Set to <code>true</code> to include snapshot versions in the resolution.
	 */
	@Parameter(property = "resolve.includeSnapshots", defaultValue = "false")
	private boolean includeSnapshots;

	/**
	 * The name of the property that will be set to the highest matching version.
	 */
	@Parameter
	private String property;

	@Component
	private RepositorySystem repoSystem;

	@Parameter(property = "resolve-range.skip", defaultValue = "true")
	private boolean skip;

	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
	private RepositorySystemSession repoSession;

	@Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
	protected List<RemoteRepository> remoteRepositories;

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if(!skip) {
			VersionRangeRequest request = new VersionRangeRequest();
			request.setRepositories(remoteRepositories);
			DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, null, version);
			request.setArtifact(artifact);

			String constraintText = artifact.toString() + ", " + (includeSnapshots ? "including" : "excluding") +
				" snapshots";
			getLog().info("Resolving range for " + constraintText);

			try {
				VersionRangeResult result = repoSystem.resolveVersionRange(repoSession, request);

				// Workaround for https://issues.apache.org/jira/browse/MNG-3092
				if (!includeSnapshots) {
					result.setVersions(removeSnapshots(result.getVersions()));
				}

				getLog().debug("Constraint: " + result.getVersionConstraint());
				getLog().info("Versions in range: " + result.getVersions());

				Version highestVersion = result.getHighestVersion();

				if (highestVersion == null) {
					throw new MojoFailureException("No matching version found for constraint: '" + constraintText + "'.");
				}

				getLog().info("Highest version: " + highestVersion);

				// Print directly to console.
				if (print) {
					System.out.println(highestVersion);
				}

				// Set property.
				if (StringUtils.isNotEmpty(property) && project != null) {
					getLog().info("Setting property " + property + "=" + highestVersion);
					project.getProperties().put(property, highestVersion.toString());
				}

			} catch (VersionRangeResolutionException e) {
				throw new MojoExecutionException("", e);
			}
		}
	}

	private List<Version> removeSnapshots(List<Version> versions) {
		ArrayList<Version> result = new ArrayList<>();
		for (Version version : versions) {
			if (!isSnapshot(version.toString())) {
				result.add(version);
			}
		}
		return result;
	}

	/**
	 * Copied from org.eclipse.aether.artifact.AbstractArtifact.isSnapshot(String).
	 */
	private static boolean isSnapshot(String version) {
		return version.endsWith(SNAPSHOT) || SNAPSHOT_TIMESTAMP.matcher(version).matches();
	}
}
