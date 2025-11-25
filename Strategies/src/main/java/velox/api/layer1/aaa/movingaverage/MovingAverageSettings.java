package velox.api.layer1.aaa.movingaverage;

import java.awt.Color;

import velox.api.layer1.settings.StrategySettingsVersion;

@StrategySettingsVersion(currentVersion = 1, compatibleVersions = {})
public class MovingAverageSettings {
    
    public enum MAType {
        SMA("Simple Moving Average"),
        EMA("Exponential Moving Average"),
        WMA("Weighted Moving Average");
        
        private final String displayName;
        
        MAType(String displayName) {
            this.displayName = displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    public int period = 20;
    public MAType maType = MAType.EMA;
    public Color color = new Color(33, 150, 243); // Blue
    public int lineWidth = 2;
    
    public MovingAverageSettings() {
    }
}
