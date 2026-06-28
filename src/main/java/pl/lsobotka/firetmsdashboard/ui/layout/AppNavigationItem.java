package pl.lsobotka.firetmsdashboard.ui.layout;

import com.vaadin.flow.component.icon.VaadinIcon;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AppNavigationItem {

    AppNavigationSection section();

    String label();

    VaadinIcon icon() default VaadinIcon.FILE_TEXT;

    int order() default 0;
}
