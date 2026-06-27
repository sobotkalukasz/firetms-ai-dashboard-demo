package pl.lsobotka.firetmsdashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.junit.jupiter.api.Test;

class MainViewTest {

    @Test
    void displaysDrawerNavigationForFireTmsInvoices() {
        MainView view = new MainView();

        Details invoicesGroup = view.getInvoicesGroup();
        Button issuedButton = view.getIssuedButton();
        H1 heading = view.getViewTitle();
        HorizontalLayout summary = (HorizontalLayout) invoicesGroup.getSummary();
        Span summaryLabel = (Span) summary.getComponentAt(1);

        assertThat(summaryLabel.getText()).isEqualTo("Invoices");
        assertThat(invoicesGroup.isOpened()).isTrue();
        assertThat(issuedButton.getText()).isEqualTo("Issued");
        assertThat(heading.getText()).isEqualTo("FireTMS AI Dashboard Demo");
    }
}
