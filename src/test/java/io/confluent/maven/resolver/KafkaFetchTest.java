package io.confluent.maven.resolver;

import io.confluent.maven.resolver.ResolveKafkaVersionRangeMojo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.util.version.GenericVersionScheme;

public class KafkaFetchTest {
    private static final String CCS = "ccs-kafka";
    private static final String constrainText = "[6.0.1-1, 6.0.2-1)";
    private GenericVersionScheme genericVersionScheme = new GenericVersionScheme();
    private ResolveKafkaVersionRangeMojo resolve = new ResolveKafkaVersionRangeMojo();
    
    private static final String ONECCS = "6.0.1-1-ccs";
    private static final String TWELVECE = "6.0.1-12-ce";

    private final VersionRangeRequest request = new VersionRangeRequest();

    @Test
    public void testCECCSkafka() throws Exception {
        final VersionRangeResult Test = new VersionRangeResult(request);
        Version VONECCS = genericVersionScheme.parseVersion(ONECCS);
        Version VTWELVECE = genericVersionScheme.parseVersion(TWELVECE);
        Test.addVersion(VTWELVECE);
        Test.addVersion(VONECCS);
        Version result = resolve.fetchHighestKafkaVersionPublic(CCS, Test , constrainText);
        assertEquals(VONECCS, result);
    }
}