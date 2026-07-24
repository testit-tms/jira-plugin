package testIT.jira.tms;

import java.util.ArrayList;
import java.util.List;

public class TmsTestPlanCountsDto {
    public int total;
    public int waiting;
    public int inProgress;
    public int passed;
    public int failed;
    public int skipped;
    public int blocked;
    /** Per-plan statuses (base + custom) from analytics, including zero counts. */
    public List<TmsTestPlanStatusCountDto> statuses = new ArrayList<>();
}
