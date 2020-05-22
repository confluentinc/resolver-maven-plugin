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
 * This is a customized solution to resolves ce/ccs kafka version range to the
 * highest matching version. By default, snapshots are excluded, as a workaround
 * for <a href="https://issues.apache.org/jira/browse/MNG-3092">MNG-3092</a>.
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
 * mvn io.confluent:resolver-maven-plugin:resolve-kafka-range \
 *     -Dresolve.groupId=org.apache.kafka -Dresolve.artifactId=kafka-client "-Dresolve.versionRange=[6.0.0-1, 6.0.1-1]" \
 *     -Dresolve.CEprint -q
 * 	   -Dresolve.CCSprint -1
 * </pre>
 */
@Mojo(name = "resolve-kafka-range", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true, requiresProject = false)
public class ResolveKafkaVersionRangeMojo extends AbstractMojo {
	private static final String SNAPSHOT = "SNAPSHOT";
	private static final String CE = "ce-kafka";
	private static final String CEVersionString = "-ce";
	private static final String CCS = "ccs-kafka";
	private static final String CCSVersionString = "-ccs";
	private static final Pattern SNAPSHOT_TIMESTAMP = Pattern.compile("^(.*-)?([0-9]{8}\\.[0-9]{6}-[0-9]+)$");
	private static final String CEKAFKAVERSION = "ce.kafka.version";
	private static final String CCSKAFKAVERSION = "kafka.version";

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
	 * If <code>true</code>, the highest matching version is printed directly to the
	 * console. This can be used with {@code mvn -q}.
	 */
	@Parameter(property = "resolve.printCE")
	private boolean printCE;

	@Parameter(property = "resolve.printCCS")
	private boolean printCCS;

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

	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
	private RepositorySystemSession repoSession;

	@Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
	protected List<RemoteRepository> remoteRepositories;

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		VersionRangeRequest request = new VersionRangeRequest();
		request.setRepositories(remoteRepositories);
		DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, null, version);
		request.setArtifact(artifact);

		String constraintText = artifact.toString() + ", " + (includeSnapshots ? "including" : "excluding")
				+ " snapshots";
		getLog().info("Resolving range for " + constraintText);
		try {
			VersionRangeResult CEResult = repoSystem.resolveVersionRange(repoSession, request);
			VersionRangeResult CCSResult = repoSystem.resolveVersionRange(repoSession, request);
			Version highestCEVersion = fetchHighestKafkaVersion(CE, CEResult, constraintText);
			Version highestCCSVersion = fetchHighestKafkaVersion(CCS, CCSResult, constraintText);

			getLog().info("Highest " + CE + " version: " + highestCEVersion);
			getLog().info("Highest " + CCS + " version: " + highestCCSVersion);

			if (printCE) {
				System.out.println(highestCEVersion);
			}

			if (printCCS) {
				System.out.println(highestCCSVersion);
			}

			// Set CE property.
			if (StringUtils.isNotEmpty(CEKAFKAVERSION) && project != null) {
				getLog().info("Setting " + CEKAFKAVERSION + " property " + CEKAFKAVERSION + "=" + highestCEVersion);
				project.getProperties().put(CEKAFKAVERSION, highestCEVersion.toString());
			}

			// Set CCS property.
			if (StringUtils.isNotEmpty(CCSKAFKAVERSION) && project != null) {
				getLog().info("Setting " + CCSKAFKAVERSION + " property " + CCSKAFKAVERSION + "=" + highestCCSVersion);
				project.getProperties().put(CCSKAFKAVERSION, highestCCSVersion.toString());
			}
		} catch (VersionRangeResolutionException e) {
			throw new MojoExecutionException("", e);
		}
	}

	private Version fetchHighestKafkaVersion(String kafkaType, VersionRangeResult result, String constraintText)
			throws MojoExecutionException, MojoFailureException {

		// Workaround for https://issues.apache.org/jira/browse/MNG-3092
		try {
			getLog().debug(kafkaType + " version fetched " + result.getVersions());
			if (!includeSnapshots) {
				result.setVersions(removeSnapshots(result.getVersions()));
			}
			if (kafkaType.equals(CE)) {
				result.setVersions(includeCE(result.getVersions()));
				// if ce-kafka can not be fetched
				if (result.getVersions().isEmpty()) {
					getLog().info(CE + "can not be fetched");
				}
			} else if (kafkaType.equals(CCS)) {
				result.setVersions(includeCCS(result.getVersions()));
			} else {
				throw new MojoFailureException("kafka type " + kafkaType + "wrong or not supported");
			}

			getLog().debug(kafkaType + " Constraint: " + result.getVersionConstraint());
			getLog().info(kafkaType + " Versions in range: " + result.getVersions());

			Version highestVersion = result.getHighestVersion();

			if (highestVersion == null) {
				throw new MojoFailureException(
						"No matching " + kafkaType + " version found for constraint: '" + constraintText + "'.");
			}

			return highestVersion;
		} catch (MojoFailureException e) {
			throw new MojoExecutionException("", e);
		}
	}

	public Version fetchHighestKafkaVersionPublic(String kafkaType, VersionRangeResult result, String constraintText)
			throws MojoExecutionException, MojoFailureException {
		return fetchHighestKafkaVersion(kafkaType, result, constraintText);
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

	private List<Version> includeCE(List<Version> versions) {
		ArrayList<Version> result = new ArrayList<>();
		for (Version version : versions) {
			if (isCE(version.toString())) {
				result.add(version);
			}
		}
		return result;
	}

	private List<Version> includeCCS(List<Version> versions) {
		ArrayList<Version> result = new ArrayList<>();
		for (Version version : versions) {
			if (isCCS(version.toString())) {
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

	private static boolean isCCS(String version) {
		return version.endsWith(CCSVersionString);
	}

	private static boolean isCE(String version) {
		return version.endsWith(CEVersionString);
	}
}
