package pl.lsobotka.firetmsdashboard;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("")
@PageTitle("FireTMS AI Dashboard Demo")
public class MainView extends VerticalLayout {

    public static final String HEADING = "FireTMS AI Dashboard Demo";

    public MainView() {
        add(new H1(HEADING));
    }
}
