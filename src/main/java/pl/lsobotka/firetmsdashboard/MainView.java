package pl.lsobotka.firetmsdashboard;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("")
@PageTitle("FireTMS AI Dashboard Demo")
public class MainView extends AppLayout {

    public static final String HEADING = "FireTMS AI Dashboard Demo";
    private static final String FIRETMS_LABEL = "FireTMS";
    private static final String INVOICES_LABEL = "Invoices";
    private static final String ISSUED_LABEL = "Issued";

    private final H3 invoicesGroup = new H3(INVOICES_LABEL);
    private final Anchor issuedLink = new Anchor("/firetms/sales-invoices", ISSUED_LABEL);

    public MainView() {
        addToNavbar(new DrawerToggle(), new H1(HEADING));
        addToDrawer(buildNavigation());
        setContent(buildHomeContent());
    }

    private VerticalLayout buildNavigation() {
        VerticalLayout navigation = new VerticalLayout();
        navigation.setPadding(true);
        navigation.setSpacing(false);
        navigation.setWidthFull();

        navigation.add(new H2(FIRETMS_LABEL));
        navigation.add(invoicesGroup);
        navigation.add(issuedLink);
        return navigation;
    }

    private VerticalLayout buildHomeContent() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.add(new H1(HEADING));
        return content;
    }

    H3 getInvoicesGroup() {
        return invoicesGroup;
    }

    Anchor getIssuedLink() {
        return issuedLink;
    }
}
