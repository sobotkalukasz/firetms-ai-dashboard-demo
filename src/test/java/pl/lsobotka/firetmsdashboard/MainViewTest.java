package pl.lsobotka.firetmsdashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H1;
import org.junit.jupiter.api.Test;

class MainViewTest {

    @Test
    void displaysDashboardHeading() {
        MainView view = new MainView();

        Component heading = view.getComponentAt(0);

        assertThat(heading).isInstanceOf(H1.class);
        assertThat(((H1) heading).getText()).isEqualTo("FireTMS AI Dashboard Demo");
    }
}
