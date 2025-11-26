package velox.api.layer1.aaa.vwap;

import java.awt.Color;

import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1SimpleAttachable;
import velox.api.layer1.annotations.Layer1StrategyName;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.layers.utils.OrderBook;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator.GraphType;
import velox.api.layer1.simplified.Api;
import velox.api.layer1.simplified.Bar;
import velox.api.layer1.simplified.BarDataListener;
import velox.api.layer1.simplified.CustomModule;
import velox.api.layer1.simplified.HistoricalDataListener;
import velox.api.layer1.simplified.Indicator;
import velox.api.layer1.simplified.InitialState;
import velox.api.layer1.simplified.IntervalListener;
import velox.api.layer1.simplified.Intervals;
import velox.api.layer1.simplified.Parameter;
import velox.api.layer1.simplified.TimeListener;

/**
 * VWAP (Volume Weighted Average Price) Indicator for Bookmap using Simplified API
 * Uses the built-in VWAP data from Bar objects
 */
@Layer1SimpleAttachable
@Layer1StrategyName("QI VWAP")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class Layer1ApiVWAP implements CustomModule, BarDataListener, HistoricalDataListener, TimeListener, IntervalListener {
    
    @Parameter(name = "Color")
    public Color color = new Color(250, 128, 114); // Salmon color
    
    @Parameter(name = "Line Width", step = 1.0)
    public Double lineWidth = 3.0;
    
    @Parameter(name = "Interval (seconds)", step = 1.0)
    public Double intervalSeconds = 5.0;
    
    @Parameter(name = "Smooth Lines (interpolate on trades)")
    public Boolean smoothLines = true;
    
    private Indicator vwapIndicator;
    private VWAPCalculator calculator;
    
    // For smooth interpolation
    private double lastVwapValue = Double.NaN;
    private double prevVwapValue = Double.NaN;
    private long lastBarTime = 0;
    private long currentTime = 0;
    private long barIntervalNanos = 0;
    
    @Override
    public void initialize(String alias, InstrumentInfo info, Api api, InitialState initialState) {
        Log.info("QI VWAP: Initializing for " + alias + ", color=salmon, lineWidth=" + lineWidth + ", smoothLines=" + smoothLines);
        
        vwapIndicator = api.registerIndicator("VWAP", GraphType.PRIMARY);
        vwapIndicator.setColor(color);
        vwapIndicator.setWidth(lineWidth.intValue());
        
        Log.info("QI VWAP: Indicator registered with line width " + lineWidth);
        
        calculator = new VWAPCalculator();
    }
    
    @Override
    public void stop() {
        Log.info("QI VWAP: Stopping");
    }
    
    @Override
    public void onBar(OrderBook orderBook, Bar bar) {
        if (calculator == null) {
            Log.warn("QI VWAP: Calculator is null!");
            return;
        }
        
        // Update timing for interpolation
        lastBarTime = currentTime;
        barIntervalNanos = (long)(intervalSeconds * 1_000_000_000L);
        
        // Calculate typical price from bar OHLC data
        double typicalPrice = (bar.getHigh() + bar.getLow() + bar.getClose()) / 3.0;
        
        // Use price range as volume proxy (since volume not available in simplified Bar)
        // This creates a volume-like weighted average
        double priceRange = bar.getHigh() - bar.getLow();
        double weight = priceRange > 0 ? priceRange : 1.0;
        
        double vwapValue = calculator.addBar(typicalPrice, weight);
        
        // Log first 5 bars, then every 20th
        if (calculator.getCount() <= 5 || calculator.getCount() % 20 == 0) {
            Log.info("QI VWAP: bar#" + calculator.getCount() + 
                    ", typicalPrice=" + String.format("%.2f", typicalPrice) +
                    ", VWAP=" + (Double.isNaN(vwapValue) ? "warming" : String.format("%.2f", vwapValue)));
        }
        
        // Store previous values for interpolation
        if (smoothLines) {
            prevVwapValue = lastVwapValue;
            lastVwapValue = vwapValue;
        }
        
        // Only update indicator if smooth lines is disabled
        // (when enabled, trades will update them)
        if (!smoothLines) {
            if (!Double.isNaN(vwapValue)) {
                vwapIndicator.addPoint(vwapValue);
            }
        }
    }
    
    @Override
    public void onTimestamp(long t) {
        currentTime = t;
        
        if (!smoothLines || Double.isNaN(lastVwapValue)) {
            return; // Only interpolate if smooth lines enabled and we have data
        }
        
        // Calculate interpolation ratio based on time elapsed in current bar
        double ratio = 0.0;
        if (lastBarTime > 0 && barIntervalNanos > 0) {
            long elapsedInBar = currentTime - lastBarTime;
            ratio = Math.max(0.0, Math.min(1.0, (double) elapsedInBar / barIntervalNanos));
        }
        
        // Linear interpolation between previous and current VWAP values
        if (!Double.isNaN(lastVwapValue)) {
            double interpolated = interpolate(prevVwapValue, lastVwapValue, ratio);
            vwapIndicator.addPoint(interpolated);
        }
    }
    
    @Override
    public void onInterval() {
        // Required by IntervalListener interface
    }
    
    /**
     * Linear interpolation between two values
     */
    private double interpolate(double prev, double current, double ratio) {
        if (Double.isNaN(prev)) {
            return current; // No previous value, use current
        }
        if (Double.isNaN(current)) {
            return prev; // No current value, use previous
        }
        return prev + (current - prev) * ratio;
    }
    
    @Override
    public long getInterval() {
        // Convert seconds to nanoseconds
        return (long)(intervalSeconds * 1_000_000_000L);
    }
    
    /**
     * Helper class to calculate VWAP
     * Uses weighted price averaging where weight represents volume/liquidity
     */
    private static class VWAPCalculator {
        private double cumulativePW = 0.0; // Cumulative Price * Weight
        private double cumulativeWeight = 0.0; // Cumulative Weight
        private int count = 0;
        
        public int getCount() {
            return count;
        }
        
        public double addBar(double price, double weight) {
            if (Double.isNaN(price) || weight <= 0) {
                return Double.isNaN(price) ? Double.NaN : price;
            }
            
            count++;
            
            // Add to cumulative values
            cumulativePW += price * weight;
            cumulativeWeight += weight;
            
            if (cumulativeWeight == 0) {
                return Double.NaN;
            }
            
            // VWAP = Cumulative(Price * Weight) / Cumulative(Weight)
            return cumulativePW / cumulativeWeight;
        }
    }
}
