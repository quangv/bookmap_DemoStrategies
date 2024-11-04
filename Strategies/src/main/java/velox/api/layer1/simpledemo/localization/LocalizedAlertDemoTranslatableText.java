package velox.api.layer1.simpledemo.localization;

import java.util.Map;
import velox.gui.utils.localization.translatable.TranslatableText;

public class LocalizedAlertDemoTranslatableText extends TranslatableText {
    
    public LocalizedAlertDemoTranslatableText(String key, Map<String, Object> args) {
        super(key, args, Layer1ApiLocalizedAlertDemo.LOCALIZED_BUNDLE_NAME, LocalizedAlertDemoTranslatableText.class.getClassLoader(), false);
    }
    
    public LocalizedAlertDemoTranslatableText(String key) {
        this(key, Map.of());
    }
}
