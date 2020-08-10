package io.confluent.maven.resolver;

import java.io.IOException;
import java.io.File;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.versions.api.PomHelper;
import org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.stax2.XMLInputFactory2;
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
	private static final Pattern SNAPSHOT_TIMESTAMP = Pattern.compile("^(.*-)?([0-9]{8}\\.[0-9]{6}-[0-9]+)$");
	private static final String CE_KAFKA_VERSION = "ce.kafka.version";
	private static final String CCS_KAFKA_VERSION = "kafka.version";

	enum Strings {
		CE("ce-kafka"), CCS("ccs-kafka"), SNAPSHOT("SNAPSHOT"), CE_QUALIFIER("-ce"), CCS_QUALIFIER("-ccs");

		private final String text;

		/**
		 * @param text
		 */
		Strings(final String text) {
			this.text = text;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Enum#toString()
		 */
		@Override
		public String toString() {
			return text;
		}
	}

	/**
	 * The group id of the artifact to resolve.
	 */
	@Parameter(property = "groupId", required = true)
	private String groupId;

	/**
	 * The artifact id of the artifact to resolve.
	 */
	@Parameter(property = "artifactId", required = true)
	private String artifactId;

	/**
	 * The version range of the artifact to resolve.
	 */
	@Parameter(property = "versionRange", required = true)
	private String versionRange;

	/**
	 * If <code>true</code>, the highest matching version is printed directly to the
	 * console. This can be used with {@code mvn -q}.
	 */
	@Parameter(property = "printCE")
	private boolean printCE;

	@Parameter(property = "printCCS")
	private boolean printCCS;

	/**
	 * Set to <code>true</code> to include snapshot versions in the resolution.
	 */
	@Parameter(property = "includeSnapshots", defaultValue = "false")
	private boolean includeSnapshots;

	@Parameter(property = "resolver.skip", defaultValue = "false")
	private boolean skip;

	/**
	 * The name of the new pom file to create.
	 */
	@Parameter(property = "resolver.new.pom.file")
	private String newPomFile;

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
		if(!skip) {
			VersionRangeRequest request = new VersionRangeRequest();
			request.setRepositories(remoteRepositories);
			DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, null, versionRange);
			request.setArtifact(artifact);

			String constraintText = artifact.toString() + ", " + (includeSnapshots ? "including" : "excluding")
					+ " snapshots";
			getLog().info("Resolving range for " + constraintText);
			try {
				VersionRangeResult CEResult = repoSystem.resolveVersionRange(repoSession, request);
				VersionRangeResult CCSResult = repoSystem.resolveVersionRange(repoSession, request);
				Version highestCEVersion = fetchHighestKafkaVersion(Strings.CE.toString(), CEResult, constraintText);
				Version highestCCSVersion = fetchHighestKafkaVersion(Strings.CCS.toString(), CCSResult, constraintText);

				getLog().info("Highest " + Strings.CE.toString() + " version: " + highestCEVersion);
				getLog().info("Highest " + Strings.CCS.toString() + " version: " + highestCCSVersion);

				if (printCE) {
					System.out.println(highestCEVersion);
				}

				if (printCCS) {
					System.out.println(highestCCSVersion);
				}

				// Set CE property.
				if (project != null) {
					getLog().info("Setting " + CE_KAFKA_VERSION + " property " + CE_KAFKA_VERSION + "=" + highestCEVersion);
					project.getProperties().put(CE_KAFKA_VERSION, highestCEVersion.toString());
				}

				// Set CCS property.
				if (project != null) {
					getLog().info(
							"Setting " + CCS_KAFKA_VERSION + " property " + CCS_KAFKA_VERSION + "=" + highestCCSVersion);
					project.getProperties().put(CCS_KAFKA_VERSION, highestCCSVersion.toString());
				}

        if (newPomFile != null) {
				  createInstalledPom(highestCEVersion.toString(), highestCCSVersion.toString());
        }
			} catch (VersionRangeResolutionException e) {
				throw new MojoExecutionException("", e);
			}
		}
	}

	//public for test
	public Version fetchHighestKafkaVersion(String kafkaType, VersionRangeResult result, String constraintText)
			throws MojoExecutionException, MojoFailureException {

		// Workaround for https://issues.apache.org/jira/browse/MNG-3092
		try {
			getLog().debug(kafkaType + " version fetched " + result.getVersions());
			if (!includeSnapshots) {
				result.setVersions(removeSnapshots(result.getVersions()));
			}
			if (kafkaType.equals(Strings.CE.toString())) {
				result.setVersions(includeCE(result.getVersions()));
				// if ce-kafka can not be fetched
				if (result.getVersions().isEmpty()) {
					getLog().info(Strings.CE.toString() + " can not be fetched");
				}
			} else if (kafkaType.equals(Strings.CCS.toString())) {
				result.setVersions(includeCCS(result.getVersions()));
			} else {
				throw new MojoFailureException("kafka type " + kafkaType + "wrong or not supported");
			}

			getLog().debug(kafkaType + " Constraint: " + result.getVersionConstraint());
			getLog().debug(kafkaType + " Versions in range: " + result.getVersions());

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
   * Loads the current pom file, sets the values for the kafka properties
   * to the newest versions, and then writes out that pom file.
   *
   * @param ceVersion The version of CE Kafka
   * @param ccsVersion The version of CCS Kafka
   * @throws MojoExecutionException If there is a failure creating the pom file or setting the properties
   */
  public void createInstalledPom(String ceVersion, String ccsVersion) throws MojoExecutionException
  {
    try {
      getLog().info("Creating installed pom file");
      // Get the name of the current projects pom file
      File pomFile = project.getFile();
      // Create the path for the new pom file we are going to create.
      Path installedPomPath = Paths.get(pomFile.getParent(), newPomFile);
      File installedPomFile = new File(installedPomPath.toString());
      // Parse the current projects pom file.
      StringBuilder input = PomHelper.readXmlFile(pomFile);
      ModifiedPomXMLEventReader newPom = newModifiedPomXER(input);

      if (!PomHelper.setPropertyVersion(newPom, null, "ce.kafka.version", ceVersion)) {
        throw new MojoExecutionException("Failed to set ce.kafka.version property.");
      }

      if (!PomHelper.setPropertyVersion(newPom, null, "kafka.version", ccsVersion)) {
        throw new MojoExecutionException("Failed to set kafka.version property.");
      }

      writeFile(installedPomFile, input);
    }
    catch (IOException e)
    {
      getLog().error(e);
      throw new MojoExecutionException("Failed to write installed pom file.", e);
    }
    catch (XMLStreamException e)
    {
      getLog().error(e);
      throw new MojoExecutionException("Failed to write installed pom file.", e);
    }
  }

  /**
   * This code was taken from the versions maven plugin .
   * https://github.com/mojohaus/versions-maven-plugin/blob/versions-maven-plugin-2.7/src/main/java/org/codehaus/mojo/versions/AbstractVersionsUpdaterMojo.java
   */
  /**
   * Creates a {@link org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader} from a StringBuilder.
   *
   * @param input The XML to read and modify.
   * @return The {@link org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader}.
   */
  protected final ModifiedPomXMLEventReader newModifiedPomXER(StringBuilder input)
  {
    ModifiedPomXMLEventReader newPom = null;
    try
    {
      XMLInputFactory inputFactory = XMLInputFactory2.newInstance();
      inputFactory.setProperty(XMLInputFactory2.P_PRESERVE_LOCATION, Boolean.TRUE);
      newPom = new ModifiedPomXMLEventReader(input, inputFactory);
    }
    catch (XMLStreamException e)
    {
      getLog().error(e);
    }
    return newPom;
  }

  /**
   * This code was taken from the versions maven plugin .
   * https://github.com/mojohaus/versions-maven-plugin/blob/versions-maven-plugin-2.7/src/main/java/org/codehaus/mojo/versions/AbstractVersionsUpdaterMojo.java
   */
  /**
   * Writes a StringBuilder into a file.
   *
   * @param outFile The file to read.
   * @param input The contents of the file.
   * @throws IOException when things go wrong.
   */
  protected final void writeFile(File outFile, StringBuilder input)
      throws IOException
  {
    Writer writer = WriterFactory.newXmlWriter(outFile);
    try
    {
      IOUtil.copy(input.toString(), writer);
    }
    finally
    {
      IOUtil.close(writer);
    }
  }

	/**
	 * Copied from org.eclipse.aether.artifact.AbstractArtifact.isSnapshot(String).
	 */
	private static boolean isSnapshot(String version) {
		return version.endsWith(Strings.SNAPSHOT.toString()) || SNAPSHOT_TIMESTAMP.matcher(version).matches();
	}

	private static boolean isCCS(String version) {
		return version.endsWith(Strings.CCS_QUALIFIER.toString());
	}

	private static boolean isCE(String version) {
		return version.endsWith(Strings.CE_QUALIFIER.toString());
	}
}
