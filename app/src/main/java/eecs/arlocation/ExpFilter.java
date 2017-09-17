package eecs.arlocation;

/**
 * Created by Brandon on 9/17/2017.
 */

public class ExpFilter {
    private double weight;
    private double value;
    private boolean first = true;

    public ExpFilter(double weight) {
        // 0 for new updates to have no effect - 1 for new updates to completely replace
        this.weight = weight;
    }

    public double update(double obs) {
        if (first) {
            first = false;
            value = obs;
        } else {
            value = weight * obs + (1 - weight) * value;
        }
        return value;
    }

    public double getValue() {
        return value;
    }
}
