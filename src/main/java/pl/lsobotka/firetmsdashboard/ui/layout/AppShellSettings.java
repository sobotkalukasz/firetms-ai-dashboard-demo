package pl.lsobotka.firetmsdashboard.ui.layout;

public record AppShellSettings(String applicationTitle, String brandTitle, String brandSubtitle) {

    public static AppShellSettings defaultSettings() {
        return new AppShellSettings(
                "FireTMS AI Dashboard Demo",
                "FireTMS",
                "Operational dashboard");
    }
}
