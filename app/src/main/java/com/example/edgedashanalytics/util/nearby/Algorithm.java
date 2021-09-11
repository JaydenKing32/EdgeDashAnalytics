package com.example.edgedashanalytics.util.nearby;

import java.util.Comparator;
import java.util.List;

class Algorithm {
    enum AlgorithmKey {
        round_robin,
        fastest,
        least_busy
    }

    static final AlgorithmKey DEFAULT_ALGORITHM = AlgorithmKey.fastest;

    /**
     * @return next endpoint in round-robin sequence
     */
    static Endpoint getRoundRobinEndpoint(List<Endpoint> endpoints, int transferCount) {
        int nextIndex = transferCount % endpoints.size();
        return endpoints.get(nextIndex);
    }

    /**
     * @return an inactive endpoint with the most completed summarisations, or the endpoint with the shortest job queue
     */
    static Endpoint getFastestEndpoint(List<Endpoint> endpoints) {
        int maxComplete = Integer.MIN_VALUE;
        int minJobs = Integer.MAX_VALUE;
        Endpoint fastest = null;

        for (Endpoint endpoint : endpoints) {
            if (!endpoint.isActive() && endpoint.completeCount > maxComplete) {
                maxComplete = endpoint.completeCount;
                fastest = endpoint;
            }
        }

        if (fastest != null) {
            return fastest;
        }

        // No free workers, so just choose the one with the shortest job queue, use completion count as tiebreaker
        for (Endpoint endpoint : endpoints) {
            if (endpoint.getJobCount() < minJobs ||
                    (endpoint.getJobCount() == minJobs && endpoint.completeCount > maxComplete)) {
                maxComplete = endpoint.completeCount;
                minJobs = endpoint.getJobCount();
                fastest = endpoint;
            }
        }

        return fastest;
    }

    /**
     * @return the endpoint with the smallest job queue
     */
    static Endpoint getLeastBusyEndpoint(List<Endpoint> endpoints) {
        return endpoints.stream().min(Comparator.comparing(Endpoint::getJobCount)).orElse(null);
    }
}
