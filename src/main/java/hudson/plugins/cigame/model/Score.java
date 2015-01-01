package hudson.plugins.cigame.model;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Recorded score for a rule and build.
 * 
 */
@ExportedBean(defaultVisibility=999)
public class Score implements Comparable<Score> {
    private final String rulesetName;
    private final String ruleName;
    private final double value;
    private final String description;
    private final String badge;

    public Score(String rulesetName, String ruleName, double points, String pointDescription, String badge) {
        this.rulesetName = rulesetName;
        this.ruleName = ruleName;
        this.value = points;
        this.description = pointDescription;
        this.badge = badge;
    }

    @Exported
    public String getDescription() {
        if (description == null) {
            return rulesetName + " - " + ruleName; //$NON-NLS-1$
        }
        return description;
    }

    @Exported
    public String getRulesetName() {
        return rulesetName;
    }

    @Exported
    public String getRuleName() {
        return ruleName;
    }

    @Exported
    public double getValue() {
        return value;
    }

    public int compareTo(Score o) {
        if (value == o.value) {
            return description.compareToIgnoreCase(o.description);
        }
        return (int) Math.round(o.value - value);
    }

    public String getBadge(){
        return badge;
    }
}
