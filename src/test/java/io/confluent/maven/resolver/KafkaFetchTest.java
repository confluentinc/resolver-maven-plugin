package io.confluent.maven.resolver;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.util.version.GenericVersionScheme;

public class KafkaFetchTest {
    private static final String CCS = "ccs-kafka";
    private static final String constrainText = "[6.0.1-1, 6.0.2-1)";
    private GenericVersionScheme genericVersionScheme = new GenericVersionScheme();
    private ResolveKafkaVersionRangeMojo resolve = new ResolveKafkaVersionRangeMojo();
    
    private static final String ONE_CCS = "6.0.1-1-ccs";
    private static final String TWELVE_CE = "6.0.1-12-ce";

    private final VersionRangeRequest request = new VersionRangeRequest();

    @Test
    public void testCECCSkafka() throws Exception {
        final VersionRangeResult Test = new VersionRangeResult(request);
        Version VONE_CCS = genericVersionScheme.parseVersion(ONE_CCS);
        Version VTWELVE_CE = genericVersionScheme.parseVersion(TWELVE_CE);
        Test.addVersion(VTWELVE_CE);
        Test.addVersion(VONE_CCS);
        Version result = resolve.fetchHighestKafkaVersion(CCS, Test , constrainText);
        assertEquals(VONE_CCS, result);
    }
}