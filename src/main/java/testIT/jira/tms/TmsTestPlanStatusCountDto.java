package testIT.jira.tms;

public class TmsTestPlanStatusCountDto {
    public String label;
    public String statusType;
    public int count;

    public TmsTestPlanStatusCountDto() {
    }

    public TmsTestPlanStatusCountDto(String label, String statusType, int count) {
        this.label = label;
        this.statusType = statusType;
        this.count = count;
    }
}
