package pl.lsobotka.firetmsdashboard.ui.layout;

import com.vaadin.flow.component.Component;
import java.util.List;
import pl.lsobotka.firetmsdashboard.ui.AiDashboardExperimentView;
import pl.lsobotka.firetmsdashboard.ui.AiDashboardVaadinExperimentView;
import pl.lsobotka.firetmsdashboard.ui.DashboardView;
import pl.lsobotka.firetmsdashboard.ui.FireTmsSalesInvoicesView;

public final class MainNavigationConfiguration {

    private MainNavigationConfiguration() {
    }

    public static AppShellSettings shellSettings() {
        return AppShellSettings.defaultSettings();
    }

    public static List<Class<? extends Component>> navigationTargets() {
        return List.of(
                DashboardView.class,
                AiDashboardExperimentView.class,
                AiDashboardVaadinExperimentView.class,
                FireTmsSalesInvoicesView.class);
    }
}
