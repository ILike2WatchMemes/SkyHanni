package at.hannibal2.skyhanni.config.features.inventory.experimentationtable;

import at.hannibal2.skyhanni.config.FeatureToggle;
import at.hannibal2.skyhanni.config.core.config.Position;
import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

public class ExperimentationTableConfig {

    @Expose
    @ConfigOption(name = "Profit Tracker", desc = "")
    @Accordion
    public ExperimentsProfitTrackerConfig experimentsProfitTracker = new ExperimentsProfitTrackerConfig();

    @Expose
    @ConfigOption(name = "Dry-Streak Display", desc = "")
    @Accordion
    public ExperimentsDryStreakConfig dryStreakConfig = new ExperimentsDryStreakConfig();

    @Expose
    @ConfigOption(name = "Display", desc = "Shows a display with useful information while doing experiments.")
    @ConfigEditorBoolean
    @FeatureToggle
    public boolean display = false;

    @Expose
    @ConfigLink(owner = ExperimentationTableConfig.class, field = "display")
    public Position displayPosition = new Position(-220, 70, false, true);

    @Expose
    @ConfigOption(name = "Superpairs Clicks Alert", desc = "Display an alert when you reach the maximum clicks gained from Chronomatron or Ultrasequencer.")
    @ConfigEditorBoolean
    @FeatureToggle
    public boolean superpairsClicksAlert = false;

    @Expose
    @ConfigOption(name = "ULTRA-RARE Book Alert", desc = "Send a chat message, title and sound when you find an ULTRA-RARE book.")
    @ConfigEditorBoolean
    @FeatureToggle
    public boolean ultraRareBookAlert = false;

    @Expose
    @ConfigOption(name = "Guardian Reminder", desc = "Sends a warning when opening the Experimentation Table without a §9§lGuardian Pet §7equipped.")
    @ConfigEditorBoolean
    @FeatureToggle
    public boolean guardianReminder = false;
}
