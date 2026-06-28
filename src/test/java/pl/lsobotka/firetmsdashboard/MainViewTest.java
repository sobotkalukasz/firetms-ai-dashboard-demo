package pl.lsobotka.firetmsdashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.junit.jupiter.api.Test;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigation;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigationSection;

class MainViewTest {

    @Test
    void displaysDrawerNavigationForFireTmsInvoices() {
        MainView view = new MainView();

        AppNavigation navigation = view.getNavigation();
        Details dashboardGroup = navigation.getGroup(AppNavigationSection.DASHBOARD);
        Details invoicesGroup = navigation.getGroup(AppNavigationSection.INVOICES);
        Button dashboardButton = navigation.getButton(MainView.DASHBOARD_ROUTE);
        Button issuedButton = navigation.getButton(MainView.ISSUED_ROUTE);
        H1 heading = view.getViewTitle();
        HorizontalLayout dashboardSummary = (HorizontalLayout) dashboardGroup.getSummary();
        HorizontalLayout summary = (HorizontalLayout) invoicesGroup.getSummary();
        Span dashboardSummaryLabel = (Span) dashboardSummary.getComponentAt(1);
        Span summaryLabel = (Span) summary.getComponentAt(1);

        assertThat(dashboardSummaryLabel.getText()).isEqualTo("Dashboard");
        assertThat(dashboardGroup.isOpened()).isTrue();
        assertThat(dashboardButton.getText()).isEqualTo("Overview");
        assertThat(summaryLabel.getText()).isEqualTo("Invoices");
        assertThat(invoicesGroup.isOpened()).isTrue();
        assertThat(issuedButton.getText()).isEqualTo("Issued");
        assertThat(heading.getText()).isEqualTo("FireTMS AI Dashboard Demo");
    }
}
