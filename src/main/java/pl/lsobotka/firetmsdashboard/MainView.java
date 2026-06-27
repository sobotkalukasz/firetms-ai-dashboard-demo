package pl.lsobotka.firetmsdashboard;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.component.dependency.CssImport;

@CssImport("./styles/main-view.css")
public class MainView extends AppLayout implements AfterNavigationObserver {

    public static final String DEFAULT_HEADING = "FireTMS AI Dashboard Demo";
    public static final String ISSUED_ROUTE = "firetms/sales-invoices";
    private static final String FIRETMS_LABEL = "FireTMS";
    private static final String INVOICES_LABEL = "Invoices";
    private static final String ISSUED_LABEL = "Issued";
    private static final String ACTIVE_NAV_CLASS = "nav-button-active";

    private final H1 viewTitle = new H1();
    private final Details invoicesGroup = new Details();
    private final Button issuedButton = new Button(ISSUED_LABEL);

    public MainView() {
        viewTitle.setText(DEFAULT_HEADING);
        viewTitle.addClassName("app-shell-title");
        addToNavbar(new DrawerToggle(), viewTitle);
        addToDrawer(buildNavigation());
    }

    private VerticalLayout buildNavigation() {
        VerticalLayout navigation = new VerticalLayout();
        navigation.setPadding(false);
        navigation.setSpacing(true);
        navigation.setWidthFull();
        navigation.setAlignItems(Alignment.STRETCH);
        navigation.addClassName("drawer-nav");

        navigation.add(buildBrandSection(), buildMenuSection());
        return navigation;
    }

    private VerticalLayout buildBrandSection() {
        VerticalLayout brandSection = createDrawerCard();
        brandSection.addClassNames("drawer-card", "drawer-brand-card");

        H2 brandTitle = new H2(FIRETMS_LABEL);
        brandTitle.addClassName("drawer-brand-title");

        Paragraph brandDescription = new Paragraph("Operational dashboard");
        brandDescription.addClassName("drawer-brand-description");

        brandSection.add(brandTitle, brandDescription);
        return brandSection;
    }

    private VerticalLayout buildMenuSection() {
        VerticalLayout menuSection = createDrawerCard();
        menuSection.addClassNames("drawer-card", "drawer-menu-card");
        menuSection.add(configureInvoicesGroup());
        return menuSection;
    }

    private VerticalLayout createDrawerCard() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.setWidthFull();
        return section;
    }

    private Details configureInvoicesGroup() {
        HorizontalLayout summary = new HorizontalLayout();
        summary.setPadding(false);
        summary.setSpacing(true);
        summary.setAlignItems(Alignment.CENTER);
        summary.setWidthFull();
        summary.addClassName("drawer-group-summary");

        Icon sectionIcon = VaadinIcon.RECORDS.create();
        sectionIcon.setSize("16px");
        sectionIcon.addClassName("drawer-group-icon");

        Span sectionLabel = new Span(INVOICES_LABEL);
        sectionLabel.addClassName("drawer-group-label");

        summary.add(sectionIcon, sectionLabel);

        issuedButton.setWidthFull();
        issuedButton.setIcon(VaadinIcon.FILE_TEXT.create());
        issuedButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        issuedButton.addClassName("drawer-nav-button");
        issuedButton.addClickListener(event -> event.getSource().getUI().ifPresent(ui -> ui.navigate(ISSUED_ROUTE)));

        VerticalLayout items = new VerticalLayout(issuedButton);
        items.setPadding(false);
        items.setSpacing(false);
        items.setWidthFull();
        items.addClassName("drawer-group-items");

        invoicesGroup.setSummary(summary);
        invoicesGroup.removeAll();
        invoicesGroup.add(items);
        invoicesGroup.setOpened(true);
        invoicesGroup.addClassName("drawer-group");
        return invoicesGroup;
    }

    private void updateNavigationState(String currentPath) {
        boolean issuedActive = ISSUED_ROUTE.equals(currentPath);
        invoicesGroup.setOpened(issuedActive);
        styleNavigationButton(issuedButton, issuedActive);
    }

    private void styleNavigationButton(Button button, boolean active) {
        if (active) {
            button.addClassName(ACTIVE_NAV_CLASS);
        } else {
            button.removeClassName(ACTIVE_NAV_CLASS);
        }
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        viewTitle.setText(resolveCurrentPageTitle());
        updateNavigationState(event.getLocation().getPath());
    }

    private String resolveCurrentPageTitle() {
        if (getContent() == null) {
            return DEFAULT_HEADING;
        }

        PageTitle pageTitle = getContent().getClass().getAnnotation(PageTitle.class);
        return pageTitle != null ? pageTitle.value() : DEFAULT_HEADING;
    }

    Details getInvoicesGroup() {
        return invoicesGroup;
    }

    H1 getViewTitle() {
        return viewTitle;
    }

    Button getIssuedButton() {
        return issuedButton;
    }
}
