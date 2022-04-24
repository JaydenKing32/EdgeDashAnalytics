package com.example.edgedashanalytics.util.nearby;

import java.util.Comparator;
import java.util.List;

public class Algorithm {
    public enum AlgorithmKey {
        round_robin,
        fastest,
        least_busy,
        fastest_cpu,
        most_cpu_cores,
        most_ram,
        most_storage,
        highest_battery,
        max_capacity
    }

    public static final AlgorithmKey DEFAULT_ALGORITHM = AlgorithmKey.fastest;

    /**
     * @return next endpoint in round-robin sequence
     */
    static Endpoint getRoundRobinEndpoint(List<Endpoint> endpoints, int transferCount) {
        int nextIndex = transferCount % endpoints.size();
        return endpoints.get(nextIndex);
    }

    /**
     * @return an inactive endpoint with the most completions, or the endpoint with the shortest job queue
     */
    static Endpoint getFastestEndpoint(List<Endpoint> endpoints) {
        int maxComplete = Integer.MIN_VALUE;
        int minJobs = Integer.MAX_VALUE;
        Endpoint fastest = null;

        for (Endpoint endpoint : endpoints) {
            if (endpoint.isInactive() && endpoint.completeCount > maxComplete) {
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

    static Endpoint getFastestCpuEndpoint(List<Endpoint> endpoints) {
        long maxCpuHz = 0;
        int minJobs = Integer.MAX_VALUE;
        Endpoint fastest = null;

        for (Endpoint endpoint : endpoints) {
            if (endpoint.isInactive() && endpoint.hardwareInfo.cpuFreq > maxCpuHz) {
                maxCpuHz = endpoint.hardwareInfo.cpuFreq;
                fastest = endpoint;
            }
        }

        if (fastest != null) {
            return fastest;
        }

        for (Endpoint endpoint : endpoints) {
            if (endpoint.hardwareInfo.cpuFreq > maxCpuHz ||
                    endpoint.hardwareInfo.cpuFreq == maxCpuHz && endpoint.getJobCount() < minJobs) {
                maxCpuHz = endpoint.hardwareInfo.cpuFreq;
                minJobs = endpoint.getJobCount();
                fastest = endpoint;
            }
        }

        return fastest;
    }

    static Endpoint getMaxCpuCoreEndpoint(List<Endpoint> endpoints) {
        int maxCpuCores = 0;
        int minJobs = Integer.MAX_VALUE;
        Endpoint result = null;

        for (Endpoint endpoint : endpoints) {
            if (endpoint.isInactive() && endpoint.hardwareInfo.cpuCores > maxCpuCores) {
                maxCpuCores = endpoint.hardwareInfo.cpuCores;
                result = endpoint;
            }
        }

        if (result != null) {
            return result;
        }

        for (Endpoint endpoint : endpoints) {
            if (endpoint.hardwareInfo.cpuCores > maxCpuCores ||
                    endpoint.hardwareInfo.cpuCores == maxCpuCores && endpoint.getJobCount() < minJobs) {
                maxCpuCores = endpoint.hardwareInfo.cpuCores;
                minJobs = endpoint.getJobCount();
                result = endpoint;
            }
        }

        return result;
    }

    static Endpoint getMaxRamEndpoint(List<Endpoint> endpoints) {
        long maxRam = 0;
        int minJobs = Integer.MAX_VALUE;
        Endpoint result = null;

        for (Endpoint endpoint : endpoints) {
            if (endpoint.isInactive() && endpoint.hardwareInfo.availRam > maxRam) {
                maxRam = endpoint.hardwareInfo.availRam;
                result = endpoint;
            }
        }

        if (result != null) {
            return result;
        }

        for (Endpoint endpoint : endpoints) {
            if (endpoint.hardwareInfo.availRam > maxRam ||
                    endpoint.hardwareInfo.availRam == maxRam && endpoint.getJobCount() < minJobs) {
                maxRam = endpoint.hardwareInfo.availRam;
                minJobs = endpoint.getJobCount();
                result = endpoint;
            }
        }

        return result;
    }

    static Endpoint getMaxStorageEndpoint(List<Endpoint> endpoints) {
        long maxStorage = 0;
        int minJobs = Integer.MAX_VALUE;
        Endpoint result = null;

        for (Endpoint endpoint : endpoints) {
            if (endpoint.isInactive() && endpoint.hardwareInfo.availStorage > maxStorage) {
                maxStorage = endpoint.hardwareInfo.availStorage;
                result = endpoint;
            }
        }

        if (result != null) {
            return result;
        }

        for (Endpoint endpoint : endpoints) {
            if (endpoint.hardwareInfo.availStorage > maxStorage ||
                    endpoint.hardwareInfo.availStorage == maxStorage && endpoint.getJobCount() < minJobs) {
                maxStorage = endpoint.hardwareInfo.availStorage;
                minJobs = endpoint.getJobCount();
                result = endpoint;
            }
        }

        return result;
    }

    static Endpoint getMaxBatteryEndpoint(List<Endpoint> endpoints) {
        int maxBattery = 0;
        int minJobs = Integer.MAX_VALUE;
        Endpoint result = null;

        for (Endpoint endpoint : endpoints) {
            if (endpoint.isInactive() && endpoint.hardwareInfo.batteryLevel > maxBattery) {
                maxBattery = endpoint.hardwareInfo.batteryLevel;
                result = endpoint;
            }
        }

        if (result != null) {
            return result;
        }

        for (Endpoint endpoint : endpoints) {
            if (endpoint.hardwareInfo.batteryLevel > maxBattery ||
                    endpoint.hardwareInfo.batteryLevel == maxBattery && endpoint.getJobCount() < minJobs) {
                maxBattery = endpoint.hardwareInfo.batteryLevel;
                minJobs = endpoint.getJobCount();
                result = endpoint;
            }
        }

        return result;
    }

    static Endpoint getMaxCapacityEndpoint(List<Endpoint> endpoints) {
        Endpoint fastest = endpoints.stream()
                .filter(Endpoint::isInactive)
                .max(Endpoint.compareProcessing())
                .orElse(null);

        if (fastest != null) {
            return fastest;
        }

        return endpoints.stream()
                .max(Endpoint.compareProcessing().thenComparing(Endpoint::getJobCount))
                .orElse(null);
    }
}
