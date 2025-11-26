package velox.api.layer1.aaa.vwap;

import java.awt.Color;

import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1SimpleAttachable;
import velox.api.layer1.annotations.Layer1StrategyName;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.TradeInfo;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator.GraphType;
import velox.api.layer1.simplified.Api;
import velox.api.layer1.simplified.CustomModule;
import velox.api.layer1.simplified.HistoricalDataListener;
import velox.api.layer1.simplified.Indicator;
import velox.api.layer1.simplified.InitialState;
import velox.api.layer1.simplified.IntervalListener;
import velox.api.layer1.simplified.Intervals;
import velox.api.layer1.simplified.Parameter;
import velox.api.layer1.simplified.TradeDataListener;

/**
 * VWAP (Volume Weighted Average Price) Indicator for Bookmap using Simplified API
 * Updates on every trade for smooth, accurate VWAP calculation
 */
@Layer1SimpleAttachable
@Layer1StrategyName("QI VWAP")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class Layer1ApiVWAP implements CustomModule, TradeDataListener, HistoricalDataListener, IntervalListener {
    
    @Parameter(name = "Color")
    public Color color = new Color(250, 128, 114); // Salmon color
    
    @Parameter(name = "Line Width", step = 1.0)
    public Double lineWidth = 3.0;
    
    private Indicator vwapIndicator;
    private VWAPCalculator calculator;
    private double lastVwap = Double.NaN;
    
    @Override
    public void initialize(String alias, InstrumentInfo info, Api api, InitialState initialState) {
        Log.info("QI VWAP: Initializing for " + alias);
        
        vwapIndicator = api.registerIndicator("VWAP", GraphType.PRIMARY);
        vwapIndicator.setColor(color);
        vwapIndicator.setWidth(lineWidth.intValue());
        
        calculator = new VWAPCalculator();
        
        Log.info("QI VWAP: Indicator registered");
    }
    
    @Override
    public void stop() {
        Log.info("QI VWAP: Stopping");
    }
    
    @Override
    public void onTrade(double price, int size, TradeInfo tradeInfo) {
        // Update VWAP calculation on every trade
        lastVwap = calculator.addTrade(price, size);
    }
    
    @Override
    public void onInterval() {
        // Update indicator display at regular intervals for smooth rendering
        if (!Double.isNaN(lastVwap)) {
            vwapIndicator.addPoint(lastVwap);
        }
    }
    
    @Override
    public long getInterval() {
        // Update display every 100ms for smooth line
        return Intervals.INTERVAL_100_MILLISECONDS;
    }
    
    /**
     * Helper class to calculate VWAP
     * VWAP = Sum(Price * Volume) / Sum(Volume)
     */
    private static class VWAPCalculator {
        private double cumulativePV = 0.0; // Cumulative Price * Volume
        private double cumulativeVolume = 0.0; // Cumulative Volume
        
        public double addTrade(double price, int size) {
            if (Double.isNaN(price) || size <= 0) {
                return Double.isNaN(price) ? Double.NaN : getCurrentVWAP();
            }
            
            // Add to cumulative values
            cumulativePV += price * size;
            cumulativeVolume += size;
            
            return getCurrentVWAP();
        }
        
        private double getCurrentVWAP() {
            if (cumulativeVolume == 0) {
                return Double.NaN;
            }
            
            // VWAP = Cumulative(Price * Volume) / Cumulative(Volume)
            return cumulativePV / cumulativeVolume;
        }
    }
}
