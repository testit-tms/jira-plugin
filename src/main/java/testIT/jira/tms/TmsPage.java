package testIT.jira.tms;

import java.util.Collections;
import java.util.List;

public class TmsPage<T> {
    public List<T> items;
    public int skip;
    public int take;
    public int totalItems;

    public TmsPage() {
        this.items = Collections.emptyList();
    }

    public TmsPage(List<T> items, int skip, int take, int totalItems) {
        this.items = items != null ? items : Collections.emptyList();
        this.skip = skip;
        this.take = take;
        this.totalItems = totalItems;
    }
}
