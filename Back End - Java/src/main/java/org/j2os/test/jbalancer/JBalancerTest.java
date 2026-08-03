package org.j2os.test.jbalancer;

import org.j2os.platform.jbalancer.JRoundRobinBalancer;
import org.j2os.platform.jbalancer.exception.ResourceNotFoundException;
import org.j2os.platform.jbalancer.resource.JResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plain, dependency-free test suite for the {@code org.j2os.platform.jbalancer} library
 * (no test framework such as JUnit is used). Run it directly with its {@link #main(String[])}
 * method; each test case reports PASS/FAIL to standard output and a summary is printed at the end.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JBalancerTest {

    /**
     * Total number of test cases executed so far.
     */
    private static int totalTestCount = 0;

    /**
     * Number of test cases that failed so far.
     */
    private static int failedTestCount = 0;

    /**
     * Runs every test case in this suite and prints a final summary.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        testResourceNotFoundExceptionMessage();

        testJResourceRejectsNullUrls();
        testJResourceRejectsEmptyUrls();
        testJResourceSingleUrlAlwaysReturnsSameUrl();
        testJResourceCyclesInOrderAndWraps();
        testJResourceDefensivelyCopiesUrlList();
        testJResourceConcurrentAccessDistributesEvenly();
        testJResourceExtremeConcurrencyStressCheck();

        testBalancerGetInstanceReturnsSameInstance();
        testBalancerRoundRobinRotation();
        testBalancerResourceIdIsCaseInsensitive();
        testBalancerThrowsWhenResourceNotConfigured();
        testBalancerRemoveResourceThenLookupThrows();
        testBalancerReconfigureResetsRotation();
        testBalancerConfigurationMethodsReturnSameInstanceForChaining();
        testBalancerNullResourceIdThrowsNullPointerException();
        testBalancerConcurrentMultiResourceIsolationStressCheck();

        printSummary();
    }

    // ------------------------------------------------------------------
    // ResourceNotFoundException
    // ------------------------------------------------------------------

    /**
     * Verifies the exception message contains both the missing resource id and the
     * hint about how to configure it.
     */
    private static void testResourceNotFoundExceptionMessage() {
        String testName = "ResourceNotFoundException message contains resource id and configuration hint";
        ResourceNotFoundException exception = new ResourceNotFoundException("SAVE-PERSON");

        assertTrue(testName + " [contains id]", exception.getMessage().contains("SAVE-PERSON"));
        assertTrue(testName + " [contains hint]", exception.getMessage().contains("configurationResource(String resourceId, List<String> urls)"));
    }

    // ------------------------------------------------------------------
    // JResource
    // ------------------------------------------------------------------

    /**
     * Verifies the constructor rejects a null url list.
     */
    private static void testJResourceRejectsNullUrls() {
        String testName = "JResource constructor rejects a null url list";
        try {
            new JResource(null);
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        }
    }

    /**
     * Verifies the constructor rejects an empty url list.
     */
    private static void testJResourceRejectsEmptyUrls() {
        String testName = "JResource constructor rejects an empty url list";
        try {
            new JResource(List.of());
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        }
    }

    /**
     * Verifies a single-url resource always returns that same url.
     */
    private static void testJResourceSingleUrlAlwaysReturnsSameUrl() {
        String testName = "JResource with a single url always returns that url";
        JResource resource = new JResource(List.of("http://only.example.com"));

        boolean allMatch = true;
        for (int i = 0; i < 5; i++) {
            allMatch &= "http://only.example.com".equals(resource.getNextUrl());
        }
        assertTrue(testName, allMatch);
    }

    /**
     * Verifies getNextUrl cycles through urls in order and wraps back around.
     */
    private static void testJResourceCyclesInOrderAndWraps() {
        String testName = "JResource.getNextUrl cycles in order and wraps around";
        JResource resource = new JResource(List.of("http://a.example.com", "http://b.example.com", "http://c.example.com"));

        boolean sequenceIsCorrect =
                "http://a.example.com".equals(resource.getNextUrl())
                        && "http://b.example.com".equals(resource.getNextUrl())
                        && "http://c.example.com".equals(resource.getNextUrl())
                        // Wraps back to the first url.
                        && "http://a.example.com".equals(resource.getNextUrl())
                        && "http://b.example.com".equals(resource.getNextUrl());

        assertTrue(testName, sequenceIsCorrect);
    }

    /**
     * Verifies mutating the caller's list after construction does not affect the resource.
     */
    private static void testJResourceDefensivelyCopiesUrlList() {
        String testName = "JResource defensively copies the url list";
        List<String> mutableUrls = new ArrayList<>(List.of("http://a.example.com", "http://b.example.com"));
        JResource resource = new JResource(mutableUrls);

        mutableUrls.add("http://c.example.com");
        mutableUrls.set(0, "http://tampered.example.com");

        boolean stillRotatesOriginalTwoUrls =
                "http://a.example.com".equals(resource.getNextUrl())
                        && "http://b.example.com".equals(resource.getNextUrl())
                        && "http://a.example.com".equals(resource.getNextUrl());

        assertTrue(testName, stillRotatesOriginalTwoUrls);
    }

    /**
     * Verifies concurrent calls to getNextUrl never throw and distribute evenly
     * across the configured urls.
     */
    private static void testJResourceConcurrentAccessDistributesEvenly() {
        String testName = "JResource.getNextUrl distributes evenly under concurrent access";
        List<String> urls = List.of("http://a.example.com", "http://b.example.com", "http://c.example.com", "http://d.example.com");
        JResource resource = new JResource(urls);

        int threadCount = 8;
        int callsPerThread = 500;
        int totalCalls = threadCount * callsPerThread;

        AtomicInteger[] countsByUrlIndex = new AtomicInteger[urls.size()];
        for (int i = 0; i < countsByUrlIndex.length; i++) {
            countsByUrlIndex[i] = new AtomicInteger(0);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int c = 0; c < callsPerThread; c++) {
                        String url = resource.getNextUrl();
                        int urlIndex = urls.indexOf(url);
                        countsByUrlIndex[urlIndex].incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finishedInTime;
        try {
            finishedInTime = doneLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finishedInTime = false;
        }
        executorService.shutdown();

        int sumOfCounts = 0;
        boolean evenlyDistributed = true;
        for (AtomicInteger count : countsByUrlIndex) {
            sumOfCounts += count.get();
            // With a round-robin rotation over 4 urls and a total call count that
            // is a multiple of 4, each url should have been returned exactly the same number of times.
            evenlyDistributed &= (count.get() == totalCalls / urls.size());
        }

        assertTrue(testName, finishedInTime && evenlyDistributed && sumOfCounts == totalCalls);
    }

    /**
     * A much heavier version of {@link #testJResourceConcurrentAccessDistributesEvenly}: an
     * irregular url count (7, not a power of 2) and far higher concurrency/volume (35 threads,
     * 70,000 total calls), to confirm the round-robin guarantee holds at scale and isn't just an
     * artifact of the smaller numbers used elsewhere. Still uses a totalCalls that's an exact
     * multiple of the url count, so exact-equality fairness can be asserted rather than a
     * tolerance-based approximation.
     */
    private static void testJResourceExtremeConcurrencyStressCheck() {
        String testName = "JResource.getNextUrl holds exact round-robin fairness under extreme concurrency (7 urls, 35 threads, 70k calls)";
        List<String> urls = List.of(
                "http://n0.example.com", "http://n1.example.com", "http://n2.example.com",
                "http://n3.example.com", "http://n4.example.com", "http://n5.example.com",
                "http://n6.example.com");
        JResource resource = new JResource(urls);

        int threadCount = 35;
        int callsPerThread = 2000;
        int totalCalls = threadCount * callsPerThread; // 70,000 - an exact multiple of 7

        Map<String, AtomicInteger> countsByUrl = new ConcurrentHashMap<>();
        for (String url : urls) {
            countsByUrl.put(url, new AtomicInteger(0));
        }
        AtomicInteger unexpectedUrlCount = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int c = 0; c < callsPerThread; c++) {
                        String url = resource.getNextUrl();
                        AtomicInteger count = countsByUrl.get(url);
                        if (count == null) {
                            unexpectedUrlCount.incrementAndGet();
                        } else {
                            count.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finishedInTime;
        try {
            finishedInTime = doneLatch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finishedInTime = false;
        }
        executorService.shutdown();

        int expectedCountPerUrl = totalCalls / urls.size();
        int sumOfCounts = 0;
        boolean everyUrlExactlyFair = true;
        for (AtomicInteger count : countsByUrl.values()) {
            sumOfCounts += count.get();
            everyUrlExactlyFair &= (count.get() == expectedCountPerUrl);
        }

        assertTrue(testName,
                finishedInTime
                        && unexpectedUrlCount.get() == 0
                        && sumOfCounts == totalCalls
                        && everyUrlExactlyFair);
    }

    // ------------------------------------------------------------------
    // JRoundRobinBalancer
    // ------------------------------------------------------------------

    /**
     * Verifies getInstance always returns the same singleton instance.
     */
    private static void testBalancerGetInstanceReturnsSameInstance() {
        String testName = "JRoundRobinBalancer.getInstance always returns the same instance";
        assertTrue(testName, JRoundRobinBalancer.getInstance() == JRoundRobinBalancer.getInstance());
    }

    /**
     * Verifies a configured resource rotates through its urls in round-robin order.
     */
    private static void testBalancerRoundRobinRotation() {
        String testName = "JRoundRobinBalancer rotates urls in round-robin order";
        String resourceId = "test-round-robin-rotation";
        try {
            JRoundRobinBalancer.getInstance().configurationResource(
                    resourceId, List.of("http://1.1.1.1/save", "http://2.2.2.2/save"));

            boolean sequenceIsCorrect =
                    "http://1.1.1.1/save".equals(JRoundRobinBalancer.getInstance().getResourceUrl(resourceId))
                            && "http://2.2.2.2/save".equals(JRoundRobinBalancer.getInstance().getResourceUrl(resourceId))
                            && "http://1.1.1.1/save".equals(JRoundRobinBalancer.getInstance().getResourceUrl(resourceId));

            assertTrue(testName, sequenceIsCorrect);
        } catch (ResourceNotFoundException e) {
            fail(testName + " [unexpected ResourceNotFoundException: " + e.getMessage() + "]");
        } finally {
            JRoundRobinBalancer.getInstance().removeResource(resourceId);
        }
    }

    /**
     * Verifies resource identifiers are matched case-insensitively.
     */
    private static void testBalancerResourceIdIsCaseInsensitive() {
        String testName = "JRoundRobinBalancer resource id lookup is case-insensitive";
        String resourceId = "Test-Case-Insensitive";
        try {
            JRoundRobinBalancer.getInstance().configurationResource(resourceId, List.of("http://only.example.com"));

            boolean matchesRegardlessOfCase =
                    "http://only.example.com".equals(JRoundRobinBalancer.getInstance().getResourceUrl("test-case-insensitive"))
                            && "http://only.example.com".equals(JRoundRobinBalancer.getInstance().getResourceUrl("TEST-CASE-INSENSITIVE"));

            assertTrue(testName, matchesRegardlessOfCase);
        } catch (ResourceNotFoundException e) {
            fail(testName + " [unexpected ResourceNotFoundException: " + e.getMessage() + "]");
        } finally {
            JRoundRobinBalancer.getInstance().removeResource(resourceId);
        }
    }

    /**
     * Verifies looking up an unconfigured resource id throws ResourceNotFoundException.
     */
    private static void testBalancerThrowsWhenResourceNotConfigured() {
        String testName = "JRoundRobinBalancer.getResourceUrl throws ResourceNotFoundException for an unconfigured id";
        String resourceId = "test-never-configured";
        // Make sure it really is not configured, in case a previous run left it behind.
        JRoundRobinBalancer.getInstance().removeResource(resourceId);

        try {
            JRoundRobinBalancer.getInstance().getResourceUrl(resourceId);
            fail(testName + " [expected ResourceNotFoundException]");
        } catch (ResourceNotFoundException expected) {
            pass(testName);
        }
    }

    /**
     * Verifies that after removeResource, looking the resource up again throws.
     */
    private static void testBalancerRemoveResourceThenLookupThrows() {
        String testName = "JRoundRobinBalancer.getResourceUrl throws after removeResource";
        String resourceId = "test-remove-then-lookup";
        JRoundRobinBalancer.getInstance().configurationResource(resourceId, List.of("http://only.example.com"));
        JRoundRobinBalancer.getInstance().removeResource(resourceId);

        try {
            JRoundRobinBalancer.getInstance().getResourceUrl(resourceId);
            fail(testName + " [expected ResourceNotFoundException]");
        } catch (ResourceNotFoundException expected) {
            pass(testName);
        }
    }

    /**
     * Verifies reconfiguring an existing resource id replaces its urls and resets the rotation.
     */
    private static void testBalancerReconfigureResetsRotation() {
        String testName = "Reconfiguring a resource id replaces its urls and resets the rotation";
        String resourceId = "test-reconfigure";
        try {
            JRoundRobinBalancer.getInstance().configurationResource(resourceId, List.of("http://old-a.example.com", "http://old-b.example.com"));
            // Advance the rotation once before reconfiguring.
            JRoundRobinBalancer.getInstance().getResourceUrl(resourceId);

            JRoundRobinBalancer.getInstance().configurationResource(resourceId, List.of("http://new.example.com"));

            boolean onlyReturnsNewUrl = "http://new.example.com".equals(JRoundRobinBalancer.getInstance().getResourceUrl(resourceId))
                    && "http://new.example.com".equals(JRoundRobinBalancer.getInstance().getResourceUrl(resourceId));

            assertTrue(testName, onlyReturnsNewUrl);
        } catch (ResourceNotFoundException e) {
            fail(testName + " [unexpected ResourceNotFoundException: " + e.getMessage() + "]");
        } finally {
            JRoundRobinBalancer.getInstance().removeResource(resourceId);
        }
    }

    /**
     * Verifies configurationResource and removeResource return the same balancer instance, enabling chaining.
     */
    private static void testBalancerConfigurationMethodsReturnSameInstanceForChaining() {
        String testName = "configurationResource and removeResource return the balancer instance for chaining";
        String resourceId = "test-chaining";
        JRoundRobinBalancer balancer = JRoundRobinBalancer.getInstance();

        JRoundRobinBalancer returnedFromConfiguration = balancer.configurationResource(resourceId, List.of("http://only.example.com"));
        JRoundRobinBalancer returnedFromRemoval = balancer.removeResource(resourceId);

        assertTrue(testName, returnedFromConfiguration == balancer && returnedFromRemoval == balancer);
    }

    /**
     * Verifies a null resource id throws NullPointerException on lookup.
     */
    private static void testBalancerNullResourceIdThrowsNullPointerException() {
        String testName = "JRoundRobinBalancer.getResourceUrl(null) throws NullPointerException";
        try {
            JRoundRobinBalancer.getInstance().getResourceUrl(null);
            fail(testName + " [expected NullPointerException]");
        } catch (NullPointerException expected) {
            pass(testName);
        } catch (ResourceNotFoundException e) {
            fail(testName + " [expected NullPointerException but got ResourceNotFoundException]");
        }
    }

    /**
     * Heavy stress test for {@link JRoundRobinBalancer} itself - none of the other balancer
     * tests exercise concurrency at all (only {@link #testJResourceConcurrentAccessDistributesEvenly}
     * and {@link #testJResourceExtremeConcurrencyStressCheck} test a single {@link JResource}
     * under load). Registers 5 distinct resources on the shared singleton, then hits all of them
     * concurrently from many threads in random order, checking two things the map-based,
     * multi-resource singleton could plausibly get wrong under real contention that a
     * single-resource test never would:
     * <ol>
     *     <li>Isolation: a call for one resource id never returns a url belonging to a
     *     different resource id (would indicate the shared map/rotation state bled across
     *     resources).</li>
     *     <li>Per-resource fairness: within each resource, the round-robin split across its own
     *     urls stays balanced (each url's count within 1 of every other url's count for that
     *     resource) even though calls to it are interleaved unpredictably with calls to the
     *     other 4 resources.</li>
     * </ol>
     */
    private static void testBalancerConcurrentMultiResourceIsolationStressCheck() {
        String testName = "JRoundRobinBalancer keeps concurrent multi-resource access isolated and fair (5 resources, 25 threads, 50k calls)";

        int resourceCount = 5;
        int urlsPerResource = 4;
        List<String> resourceIds = new ArrayList<>();
        Map<String, List<String>> urlsByResourceId = new HashMap<>();

        for (int r = 0; r < resourceCount; r++) {
            String resourceId = "stress-resource-" + r;
            List<String> urls = new ArrayList<>();
            for (int u = 0; u < urlsPerResource; u++) {
                urls.add("http://resource" + r + "-node" + u + ".example.com");
            }
            resourceIds.add(resourceId);
            urlsByResourceId.put(resourceId, urls);
            JRoundRobinBalancer.getInstance().configurationResource(resourceId, urls);
        }

        int threadCount = 25;
        int callsPerThread = 2000;
        int totalCalls = threadCount * callsPerThread;

        // resourceId -> (url -> count)
        Map<String, Map<String, AtomicInteger>> countsByResourceThenUrl = new ConcurrentHashMap<>();
        for (String resourceId : resourceIds) {
            Map<String, AtomicInteger> countsByUrl = new ConcurrentHashMap<>();
            for (String url : urlsByResourceId.get(resourceId)) {
                countsByUrl.put(url, new AtomicInteger(0));
            }
            countsByResourceThenUrl.put(resourceId, countsByUrl);
        }
        AtomicInteger crossResourceContaminationCount = new AtomicInteger(0);
        AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int c = 0; c < callsPerThread; c++) {
                        String resourceId = resourceIds.get(ThreadLocalRandom.current().nextInt(resourceCount));
                        try {
                            String url = JRoundRobinBalancer.getInstance().getResourceUrl(resourceId);
                            AtomicInteger count = countsByResourceThenUrl.get(resourceId).get(url);
                            if (count == null) {
                                crossResourceContaminationCount.incrementAndGet();
                            } else {
                                count.incrementAndGet();
                            }
                        } catch (ResourceNotFoundException unexpected) {
                            unexpectedExceptionCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finishedInTime;
        try {
            finishedInTime = doneLatch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finishedInTime = false;
        }
        executorService.shutdown();

        int sumOfAllCounts = 0;
        boolean everyResourceFair = true;
        for (String resourceId : resourceIds) {
            Map<String, AtomicInteger> countsByUrl = countsByResourceThenUrl.get(resourceId);
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (AtomicInteger count : countsByUrl.values()) {
                int value = count.get();
                sumOfAllCounts += value;
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            // Round-robin fairness within a resource: every url's count should be within 1 of
            // every other url's count for that same resource, regardless of how the random
            // resource selection above happened to interleave calls across resources.
            everyResourceFair &= (max - min <= 1);
        }

        for (String resourceId : resourceIds) {
            JRoundRobinBalancer.getInstance().removeResource(resourceId);
        }

        assertTrue(testName,
                finishedInTime
                        && unexpectedExceptionCount.get() == 0
                        && crossResourceContaminationCount.get() == 0
                        && sumOfAllCounts == totalCalls
                        && everyResourceFair);
    }

    // ------------------------------------------------------------------
    // Minimal assertion helpers (no external test framework)
    // ------------------------------------------------------------------

    /**
     * Records a passing test case if {@code condition} is true, otherwise records a failure.
     *
     * @param testName  the name of the test case, printed in the report
     * @param condition the condition that must be true for the test to pass
     */
    private static void assertTrue(String testName, boolean condition) {
        if (condition) {
            pass(testName);
        } else {
            fail(testName);
        }
    }

    /**
     * Records and prints a passing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void pass(String testName) {
        totalTestCount++;
        System.out.println("[PASS] " + testName);
    }

    /**
     * Records and prints a failing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void fail(String testName) {
        totalTestCount++;
        failedTestCount++;
        System.out.println("[FAIL] " + testName);
    }

    /**
     * Prints a final pass/fail summary of the whole suite.
     */
    private static void printSummary() {
        int passedTestCount = totalTestCount - failedTestCount;
        System.out.println();
        System.out.println("==============================================");
        System.out.println("Total: " + totalTestCount + "  Passed: " + passedTestCount + "  Failed: " + failedTestCount);
        System.out.println(failedTestCount == 0 ? "ALL TESTS PASSED" : "SOME TESTS FAILED");
        System.out.println("==============================================");
    }
}