package pl.lsobotka.firetmsdashboard.ui.layout;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AppNavigation extends VerticalLayout {

    private static final String ACTIVE_NAV_CLASS = "nav-button-active";

    private final Map<String, Button> buttonsByRoute = new HashMap<>();
    private final Map<AppNavigationSection, Details> groupsBySection = new EnumMap<>(AppNavigationSection.class);
    private final Map<String, AppNavigationSection> sectionsByRoute = new HashMap<>();

    public AppNavigation(AppShellSettings shellSettings, List<Class<? extends Component>> navigationTargets) {
        setPadding(false);
        setSpacing(true);
        setWidthFull();
        setAlignItems(Alignment.STRETCH);
        addClassName("drawer-nav");

        add(buildBrandSection(shellSettings), buildMenuSection(resolveSections(navigationTargets)));
    }

    public void setActivePath(String currentPath) {
        buttonsByRoute.forEach((route, button) -> styleNavigationButton(button, route.equals(currentPath)));
        groupsBySection.forEach((section, details) -> details.setOpened(hasActiveItem(section, currentPath)));
    }

    public Details getGroup(AppNavigationSection section) {
        return groupsBySection.get(section);
    }

    public Button getButton(String route) {
        return buttonsByRoute.get(route);
    }

    private boolean hasActiveItem(AppNavigationSection section, String currentPath) {
        return section.equals(sectionsByRoute.get(currentPath));
    }

    private VerticalLayout buildBrandSection(AppShellSettings shellSettings) {
        VerticalLayout brandSection = createDrawerCard();
        brandSection.addClassNames("drawer-card", "drawer-brand-card");

        H2 brandTitle = new H2(shellSettings.brandTitle());
        brandTitle.addClassName("drawer-brand-title");

        Paragraph brandDescription = new Paragraph(shellSettings.brandSubtitle());
        brandDescription.addClassName("drawer-brand-description");

        brandSection.add(brandTitle, brandDescription);
        return brandSection;
    }

    private VerticalLayout buildMenuSection(List<NavigationSection> sections) {
        VerticalLayout menuSection = createDrawerCard();
        menuSection.addClassNames("drawer-card", "drawer-menu-card");
        sections.stream()
                .map(this::buildGroup)
                .forEach(menuSection::add);
        return menuSection;
    }

    private Details buildGroup(NavigationSection section) {
        HorizontalLayout summary = new HorizontalLayout();
        summary.setPadding(false);
        summary.setSpacing(true);
        summary.setAlignItems(Alignment.CENTER);
        summary.setWidthFull();
        summary.addClassName("drawer-group-summary");

        Icon sectionIcon = section.section().icon().create();
        sectionIcon.setSize("16px");
        sectionIcon.addClassName("drawer-group-icon");

        Span sectionLabel = new Span(section.section().label());
        sectionLabel.addClassName("drawer-group-label");
        summary.add(sectionIcon, sectionLabel);

        VerticalLayout items = new VerticalLayout();
        items.setPadding(false);
        items.setSpacing(false);
        items.setWidthFull();
        items.addClassName("drawer-group-items");
        section.items().stream()
                .map(this::buildButton)
                .forEach(items::add);

        Details group = new Details();
        group.setSummary(summary);
        group.add(items);
        group.setOpened(true);
        group.addClassName("drawer-group");
        groupsBySection.put(section.section(), group);
        return group;
    }

    private Button buildButton(NavigationTarget item) {
        Button button = new Button(item.label());
        button.setWidthFull();
        button.setIcon(item.icon().create());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.addClassName("drawer-nav-button");
        button.addClickListener(event -> event.getSource().getUI().ifPresent(ui -> ui.navigate(item.navigationTarget())));
        buttonsByRoute.put(item.route(), button);
        sectionsByRoute.put(item.route(), item.section());
        return button;
    }

    private VerticalLayout createDrawerCard() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.setWidthFull();
        return section;
    }

    private List<NavigationSection> resolveSections(List<Class<? extends Component>> navigationTargets) {
        return navigationTargets.stream()
                .map(NavigationTarget::from)
                .collect(Collectors.groupingBy(NavigationTarget::section))
                .entrySet()
                .stream()
                .map(entry -> new NavigationSection(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparingInt(NavigationTarget::order).thenComparing(NavigationTarget::label))
                                .toList()))
                .sorted(Comparator.comparingInt(section -> section.section().order()))
                .toList();
    }

    private void styleNavigationButton(Button button, boolean active) {
        if (active) {
            button.addClassName(ACTIVE_NAV_CLASS);
        } else {
            button.removeClassName(ACTIVE_NAV_CLASS);
        }
    }

    private record NavigationSection(AppNavigationSection section, List<NavigationTarget> items) {
    }

    private record NavigationTarget(
            Class<? extends Component> navigationTarget,
            String route,
            String label,
            VaadinIcon icon,
            AppNavigationSection section,
            int order) {

        private static NavigationTarget from(Class<? extends Component> navigationTarget) {
            Route route = navigationTarget.getAnnotation(Route.class);
            AppNavigationItem item = navigationTarget.getAnnotation(AppNavigationItem.class);
            if (route == null || item == null) {
                throw new IllegalArgumentException("Navigation target must declare both @Route and @AppNavigationItem: "
                        + navigationTarget.getName());
            }
            return new NavigationTarget(
                    navigationTarget,
                    route.value(),
                    item.label(),
                    item.icon(),
                    item.section(),
                    item.order());
        }
    }
}
