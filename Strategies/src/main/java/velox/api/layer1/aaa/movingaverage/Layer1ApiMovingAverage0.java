package velox.api.layer1.aaa.movingaverage;

import java.awt.Color;
import java.util.LinkedList;
import java.util.Queue;

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
import velox.api.layer1.aaa.movingaverage.MovingAverageSettings.MAType;

/**
 * Moving Average Indicator for Bookmap using Simplified API
 * Supports SMA, EMA, and WMA calculation methods
 */
@Layer1SimpleAttachable
@Layer1StrategyName("QI Moving Average v0")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class Layer1ApiMovingAverage0 implements CustomModule, BarDataListener, HistoricalDataListener, TimeListener, IntervalListener {
    
    @Parameter(name = "Period 1", step = 1.0)
    public Double period1 = 9.0;
    
    @Parameter(name = "Color 1")
    public Color color1 = new Color(255, 87, 51); // Orange-red
    
    @Parameter(name = "Period 2", step = 1.0)
    public Double period2 = 20.0;
    
    @Parameter(name = "Color 2")
    public Color color2 = new Color(33, 150, 243); // Blue
    
    @Parameter(name = "Period 3", step = 1.0)
    public Double period3 = 50.0;
    
    @Parameter(name = "Color 3")
    public Color color3 = new Color(76, 175, 80); // Green
    
    @Parameter(name = "MA Type (SMA/EMA/WMA)")
    public String maType = "EMA";
    
    @Parameter(name = "Interval (seconds)", step = 1.0)
    public Double intervalSeconds = 5.0;
    
    @Parameter(name = "Smooth Lines (interpolate on trades)")
    public Boolean smoothLines = true;
    
    private Indicator ma1Indicator;
    private Indicator ma2Indicator;
    private Indicator ma3Indicator;
    private MovingAverageCalculator calculator1;
    private MovingAverageCalculator calculator2;
    private MovingAverageCalculator calculator3;
    
    // For smooth interpolation
    private double lastMa1Value = Double.NaN;
    private double lastMa2Value = Double.NaN;
    private double lastMa3Value = Double.NaN;
    private double prevMa1Value = Double.NaN;
    private double prevMa2Value = Double.NaN;
    private double prevMa3Value = Double.NaN;
    private long lastBarTime = 0;
    private long currentTime = 0;
    private long barIntervalNanos = 0;
    
    @Override
    public void initialize(String alias, InstrumentInfo info, Api api, InitialState initialState) {
        Log.info("QI MA: Initializing for " + alias + ", periods=[" + period1 + "," + period2 + "," + period3 + "], type=" + maType + ", smoothLines=" + smoothLines);
        
        ma1Indicator = api.registerIndicator("MA" + period1.intValue(), GraphType.PRIMARY);
        ma1Indicator.setColor(color1);
        
        ma2Indicator = api.registerIndicator("MA" + period2.intValue(), GraphType.PRIMARY);
        ma2Indicator.setColor(color2);
        
        ma3Indicator = api.registerIndicator("MA" + period3.intValue(), GraphType.PRIMARY);
        ma3Indicator.setColor(color3);
        
        Log.info("QI MA: Indicators registered");
        
        MAType type;
        try {
            type = MAType.valueOf(maType.toUpperCase());
        } catch (IllegalArgumentException e) {
            Log.warn("QI MA: Invalid MA type '" + maType + "', defaulting to EMA. Valid types: SMA, EMA, WMA");
            type = MAType.EMA;
        }
        
        calculator1 = new MovingAverageCalculator(period1.intValue(), type);
        calculator2 = new MovingAverageCalculator(period2.intValue(), type);
        calculator3 = new MovingAverageCalculator(period3.intValue(), type);
    }
    
    @Override
    public void stop() {
        Log.info("QI MA: Stopping");
    }
    
    @Override
    public void onBar(OrderBook orderBook, Bar bar) {
        if (calculator1 == null || calculator2 == null || calculator3 == null) {
            Log.warn("QI MA: Calculators are null!");
            return;
        }
        
        // Update timing for interpolation
        lastBarTime = currentTime;
        barIntervalNanos = (long)(intervalSeconds * 1_000_000_000L);
        
        // Use close price from bar
        double price = bar.getClose();
        
        double ma1Value = calculator1.addPrice(price);
        double ma2Value = calculator2.addPrice(price);
        double ma3Value = calculator3.addPrice(price);
        
        // Log first 5 bars, then every 20th
        if (calculator1.getCount() <= 5 || calculator1.getCount() % 20 == 0) {
            Log.info("QI MA: bar#" + calculator1.getCount() + ", close=" + String.format("%.2f", price) + 
                    ", MA1=" + (Double.isNaN(ma1Value) ? "warming" : String.format("%.2f", ma1Value)) +
                    ", MA2=" + (Double.isNaN(ma2Value) ? "warming" : String.format("%.2f", ma2Value)) +
                    ", MA3=" + (Double.isNaN(ma3Value) ? "warming" : String.format("%.2f", ma3Value)));
        }
        
        // Store previous values for interpolation
        if (smoothLines) {
            prevMa1Value = lastMa1Value;
            prevMa2Value = lastMa2Value;
            prevMa3Value = lastMa3Value;
            lastMa1Value = ma1Value;
            lastMa2Value = ma2Value;
            lastMa3Value = ma3Value;
        }
        
        // Only update indicators if smooth lines is disabled
        // (when enabled, trades will update them)
        if (!smoothLines) {
            if (!Double.isNaN(ma1Value)) {
                ma1Indicator.addPoint(ma1Value);
            }
            if (!Double.isNaN(ma2Value)) {
                ma2Indicator.addPoint(ma2Value);
            }
            if (!Double.isNaN(ma3Value)) {
                ma3Indicator.addPoint(ma3Value);
            }
        }
    }
    
    @Override
    public void onTimestamp(long t) {
        currentTime = t;
        
        if (!smoothLines || Double.isNaN(lastMa1Value)) {
            return; // Only interpolate if smooth lines enabled and we have data
        }
        
        // Calculate interpolation ratio based on time elapsed in current bar
        double ratio = 0.0;
        if (lastBarTime > 0 && barIntervalNanos > 0) {
            long elapsedInBar = currentTime - lastBarTime;
            ratio = Math.max(0.0, Math.min(1.0, (double) elapsedInBar / barIntervalNanos));
        }
        
        // Linear interpolation between previous and current MA values
        if (!Double.isNaN(lastMa1Value)) {
            double interpolated = interpolate(prevMa1Value, lastMa1Value, ratio);
            ma1Indicator.addPoint(interpolated);
        }
        if (!Double.isNaN(lastMa2Value)) {
            double interpolated = interpolate(prevMa2Value, lastMa2Value, ratio);
            ma2Indicator.addPoint(interpolated);
        }
        if (!Double.isNaN(lastMa3Value)) {
            double interpolated = interpolate(prevMa3Value, lastMa3Value, ratio);
            ma3Indicator.addPoint(interpolated);
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
     * Helper class to calculate moving averages
     */
    private static class MovingAverageCalculator {
        private final int period;
        private final MAType maType;
        private final Queue<Double> priceQueue;
        private double sum = 0.0;
        private double ema = Double.NaN;
        private final double multiplier;
        private int count = 0;
        
        public MovingAverageCalculator(int period, MAType maType) {
            this.period = period;
            this.maType = maType;
            this.priceQueue = new LinkedList<>();
            this.multiplier = 2.0 / (period + 1.0);
        }
        
        public int getCount() {
            return count;
        }
        
        public double addPrice(double price) {
            if (Double.isNaN(price)) {
                return Double.NaN;
            }
            
            count++;
            
            switch (maType) {
                case SMA:
                    return calculateSMA(price);
                case EMA:
                    return calculateEMA(price);
                case WMA:
                    return calculateWMA(price);
                default:
                    return calculateSMA(price);
            }
        }
        
        private double calculateSMA(double price) {
            priceQueue.offer(price);
            sum += price;
            
            if (priceQueue.size() > period) {
                double oldPrice = priceQueue.poll();
                sum -= oldPrice;
            }
            
            if (priceQueue.size() < period) {
                return Double.NaN;
            }
            
            return sum / period;
        }
        
        private double calculateEMA(double price) {
            if (Double.isNaN(ema)) {
                // Initialize with SMA
                priceQueue.offer(price);
                sum += price;
                
                if (priceQueue.size() < period) {
                    return Double.NaN;
                }
                
                // First EMA value is SMA
                ema = sum / period;
                return ema;
            }
            
            // EMA formula: EMA = (Price - PreviousEMA) * multiplier + PreviousEMA
            ema = (price - ema) * multiplier + ema;
            return ema;
        }
        
        private double calculateWMA(double price) {
            priceQueue.offer(price);
            
            if (priceQueue.size() > period) {
                priceQueue.poll();
            }
            
            if (priceQueue.size() < period) {
                return Double.NaN;
            }
            
            // WMA calculation: sum of (price * weight) / sum of weights
            double weightedSum = 0.0;
            double weightSum = 0.0;
            int weight = 1;
            
            for (Double p : priceQueue) {
                weightedSum += p * weight;
                weightSum += weight;
                weight++;
            }
            
            return weightedSum / weightSum;
        }
    }
}
