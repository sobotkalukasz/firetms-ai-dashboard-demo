package pl.lsobotka.firetmsdashboard.ui.layout;

import com.vaadin.flow.component.icon.VaadinIcon;

public enum AppNavigationSection {
    DASHBOARD("Dashboard", VaadinIcon.DASHBOARD, 10),
    EXPERIMENTS("Experiments", VaadinIcon.SPARK_LINE, 50),
    INVOICES("Invoices", VaadinIcon.RECORDS, 100);

    private final String label;
    private final VaadinIcon icon;
    private final int order;

    AppNavigationSection(String label, VaadinIcon icon, int order) {
        this.label = label;
        this.icon = icon;
        this.order = order;
    }

    public String label() {
        return label;
    }

    public VaadinIcon icon() {
        return icon;
    }

    public int order() {
        return order;
    }
}
