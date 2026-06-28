package pl.lsobotka.firetmsdashboard;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.component.dependency.CssImport;
import pl.lsobotka.firetmsdashboard.ui.layout.AppNavigation;
import pl.lsobotka.firetmsdashboard.ui.layout.MainNavigationConfiguration;

@CssImport("./styles/main-view.css")
public class MainView extends AppLayout implements AfterNavigationObserver {

    public static final String DASHBOARD_ROUTE = "dashboard";
    public static final String ISSUED_ROUTE = "firetms/sales-invoices";
    public static final String AI_DASHBOARD_ROUTE = "ai-dashboard";

    private final H1 viewTitle = new H1();
    private final AppNavigation navigation = new AppNavigation(
            MainNavigationConfiguration.shellSettings(),
            MainNavigationConfiguration.navigationTargets());

    public MainView() {
        viewTitle.setText(MainNavigationConfiguration.shellSettings().applicationTitle());
        viewTitle.addClassName("app-shell-title");
        addToNavbar(new DrawerToggle(), viewTitle);
        addToDrawer(navigation);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        viewTitle.setText(resolveCurrentPageTitle());
        String currentPath = event.getLocation().getPath();
        navigation.setActivePath(currentPath.isBlank() ? DASHBOARD_ROUTE : currentPath);
    }

    private String resolveCurrentPageTitle() {
        if (getContent() == null) {
            return MainNavigationConfiguration.shellSettings().applicationTitle();
        }

        PageTitle pageTitle = getContent().getClass().getAnnotation(PageTitle.class);
        return pageTitle != null ? pageTitle.value() : MainNavigationConfiguration.shellSettings().applicationTitle();
    }

    AppNavigation getNavigation() {
        return navigation;
    }

    H1 getViewTitle() {
        return viewTitle;
    }
}
