package pl.lsobotka.firetmsdashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import org.junit.jupiter.api.Test;

class MainViewTest {

    @Test
    void displaysDrawerNavigationForFireTmsInvoices() {
        MainView view = new MainView();

        H3 invoicesGroup = view.getInvoicesGroup();
        Anchor issuedLink = view.getIssuedLink();
        H1 heading = (H1) ((com.vaadin.flow.component.orderedlayout.VerticalLayout) view.getContent()).getComponentAt(0);

        assertThat(invoicesGroup.getText()).isEqualTo("Invoices");
        assertThat(issuedLink.getText()).isEqualTo("Issued");
        assertThat(issuedLink.getHref()).isEqualTo("/firetms/sales-invoices");
        assertThat(heading.getText()).isEqualTo("FireTMS AI Dashboard Demo");
    }
}
