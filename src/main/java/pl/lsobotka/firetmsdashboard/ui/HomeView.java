package pl.lsobotka.firetmsdashboard.ui;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import pl.lsobotka.firetmsdashboard.MainView;

@Route(value = "", layout = MainView.class)
@PageTitle("Home")
public class HomeView extends VerticalLayout {

    public HomeView() {
        setPadding(true);
        add(new Paragraph("Select a page from the drawer."));
    }
}
