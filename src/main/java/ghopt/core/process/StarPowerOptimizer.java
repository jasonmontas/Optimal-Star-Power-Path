package ghopt.core.process;

import ghopt.core.model.ChartData;
import java.util.*;

public class StarPowerOptimizer {

    public static class OptimalPath {
        public List<Integer> activationTimes;
        public long totalScore;

        public OptimalPath(List<Integer> activationTimes, long totalScore) {
            this.activationTimes = new ArrayList<>(activationTimes);
            this.totalScore = totalScore;
        }
    }

    public static OptimalPath findOptimalPath(ChartData chartData) {
        System.out.println("Optimizer running...");
        System.out.println("Notes: " + chartData.getNotes().size());
        System.out.println("Star Power phrases: " + chartData.getStarPowerPhrases().size());
        // temporary placeholder
        return new OptimalPath(new ArrayList<>(), 0);
    }

}
