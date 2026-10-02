package testIT.jira.model;

public class IssueModel {
    private final String title;
    private final String url;

    public IssueModel(String title, String url) {
        this.title = title;
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }
}
